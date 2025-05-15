package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models._
import org.mongodb.scala.bson.ObjectId
import java.time.Instant
import scala.collection.mutable

@Singleton
class ContestService @Inject()(
  mongoService: MongoService
)(implicit ec: ExecutionContext) {
  
  case class ContestSubmission(
    userId: ObjectId,
    problemId: ObjectId,
    submissionId: ObjectId,
    score: Double,
    submittedAt: Instant
  )
  
  def createContest(contest: Contest): Future[ObjectId] = {
    val doc = Document(
      "_id" -> contest._id,
      "title" -> contest.title,
      "description" -> contest.description,
      "startTime" -> contest.startTime,
      "endTime" -> contest.endTime,
      "problems" -> contest.problems.map(p => Document(
        "problemId" -> p.problemId,
        "points" -> p.points,
        "order" -> p.order
      )),
      "visibility" -> contest.visibility,
      "organizationId" -> contest.organizationId,
      "createdBy" -> contest.createdBy,
      "createdAt" -> contest.createdAt,
      "updatedAt" -> contest.updatedAt,
      "maxParticipants" -> contest.maxParticipants,
      "registrationDeadline" -> contest.registrationDeadline,
      "rules" -> contest.rules,
      "prizes" -> contest.prizes.map(_.map { case (k, v) => k.toString -> v }.toMap)
    )
    
    mongoService.insertContest(doc).map(_ => contest._id)
  }
  
  def registerParticipant(contestId: ObjectId, userId: ObjectId): Future[Boolean] = {
    for {
      contestOpt <- mongoService.findContestById(contestId)
      result <- contestOpt match {
        case Some(contest) if isRegistrationAllowed(contest, userId) =>
          val update = set("participants." + userId.toString, Document(
            "userId" -> userId,
            "score" -> 0.0,
            "problemsSolved" -> 0,
            "lastSubmission" -> None,
            "rank" -> None
          ))
          mongoService.updateContest(contestId, update).map(_.wasAcknowledged())
        case _ => Future.successful(false)
      }
    } yield result
  }
  
  private def isRegistrationAllowed(contest: Document, userId: ObjectId): Boolean = {
    val now = Instant.now()
    val startTime = contest.get("startTime").asDateTime().getValue
    val registrationDeadline = contest.get("registrationDeadline")
      .map(_.asDateTime().getValue)
      .getOrElse(startTime)
    
    val maxParticipants = contest.get("maxParticipants")
      .map(_.asInt32().getValue)
      .getOrElse(Int.MaxValue)
    
    val currentParticipants = contest.get("participants")
      .map(_.asDocument().size)
      .getOrElse(0)
    
    now.isBefore(registrationDeadline) &&
    currentParticipants < maxParticipants &&
    !contest.get("participants")
      .map(_.asDocument().containsKey(userId.toString))
      .getOrElse(false)
  }
  
  def submitSolution(
    contestId: ObjectId,
    userId: ObjectId,
    problemId: ObjectId,
    submission: Submission
  ): Future[Boolean] = {
    for {
      contestOpt <- mongoService.findContestById(contestId)
      result <- contestOpt match {
        case Some(contest) if isSubmissionAllowed(contest, userId, problemId) =>
          processContestSubmission(contest, userId, problemId, submission)
        case _ => Future.successful(false)
      }
    } yield result
  }
  
  private def isSubmissionAllowed(
    contest: Document,
    userId: ObjectId,
    problemId: ObjectId
  ): Boolean = {
    val now = Instant.now()
    val startTime = contest.get("startTime").asDateTime().getValue
    val endTime = contest.get("endTime").asDateTime().getValue
    
    now.isAfter(startTime) &&
    now.isBefore(endTime) &&
    contest.get("participants")
      .map(_.asDocument().containsKey(userId.toString))
      .getOrElse(false) &&
    contest.get("problems")
      .map(_.asArray().getValues.exists(p =>
        p.asDocument().get("problemId").asObjectId().getValue == problemId
      ))
      .getOrElse(false)
  }
  
  private def processContestSubmission(
    contest: Document,
    userId: ObjectId,
    problemId: ObjectId,
    submission: Submission
  ): Future[Boolean] = {
    val problemPoints = contest.get("problems")
      .map(_.asArray().getValues.find(p =>
        p.asDocument().get("problemId").asObjectId().getValue == problemId
      ))
      .flatten
      .map(_.asDocument().get("points").asInt32().getValue)
      .getOrElse(0)
    
    val score = (submission.score / 100.0) * problemPoints
    
    val update = combine(
      set(s"participants.${userId}.lastSubmission", submission.submittedAt),
      inc(s"participants.${userId}.score", score),
      inc(s"participants.${userId}.problemsSolved", if (submission.score == 100.0) 1 else 0)
    )
    
    for {
      updateResult <- mongoService.updateContest(contest.get("_id").asObjectId().getValue, update)
      _ <- if (updateResult.wasAcknowledged()) updateRankings(contest.get("_id").asObjectId().getValue)
           else Future.successful(())
    } yield updateResult.wasAcknowledged()
  }
  
  private def updateRankings(contestId: ObjectId): Future[Boolean] = {
    for {
      contestOpt <- mongoService.findContestById(contestId)
      result <- contestOpt match {
        case Some(contest) =>
          val participants = contest.get("participants").asDocument()
          val sortedParticipants = participants.keySet.toList.sortBy { userId =>
            val p = participants.get(userId).asDocument()
            (-p.get("score").asDouble().getValue,
             p.get("lastSubmission").map(_.asDateTime().getValue).getOrElse(Instant.MAX))
          }
          
          val updates = sortedParticipants.zipWithIndex.map { case (userId, index) =>
            set(s"participants.$userId.rank", index + 1)
          }
          
          mongoService.updateContest(contestId, combine(updates: _*))
            .map(_.wasAcknowledged())
            
        case None => Future.successful(false)
      }
    } yield result
  }
  
  def getContestLeaderboard(contestId: ObjectId): Future[List[(String, Double, Int)]] = {
    for {
      contestOpt <- mongoService.findContestById(contestId)
      users <- contestOpt match {
        case Some(contest) =>
          val userIds = contest.get("participants")
            .asDocument()
            .keySet
            .map(new ObjectId(_))
          Future.sequence(userIds.map(mongoService.findUserById))
        case None => Future.successful(List.empty)
      }
    } yield {
      contestOpt match {
        case Some(contest) =>
          val participants = contest.get("participants").asDocument()
          participants.keySet.toList.map { userId =>
            val p = participants.get(userId).asDocument()
            val username = users.find(_.exists(_.get("_id").asObjectId().getValue.toString == userId))
              .flatten
              .map(_.get("username").asString().getValue)
              .getOrElse("Unknown")
            
            (username,
             p.get("score").asDouble().getValue,
             p.get("rank").asInt32().getValue)
          }.sortBy(_._3)
        case None => List.empty
      }
    }
  }
} 