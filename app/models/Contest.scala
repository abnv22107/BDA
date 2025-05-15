package models

import org.mongodb.scala.bson.ObjectId
import java.time.Instant

case class ContestProblem(
  problemId: ObjectId,
  points: Int,
  order: Int
)

case class Participant(
  userId: ObjectId,
  score: Double = 0.0,
  problemsSolved: Int = 0,
  lastSubmission: Option[Instant] = None,
  rank: Option[Int] = None
)

object ContestStatus extends Enumeration {
  type ContestStatus = Value
  val Upcoming, Running, Ended = Value
}

case class Contest(
  _id: ObjectId = new ObjectId(),
  title: String,
  description: String,
  startTime: Instant,
  endTime: Instant,
  problems: List[ContestProblem],
  participants: Map[ObjectId, Participant] = Map.empty,
  visibility: String = "Public", // Public, Private, Organization
  organizationId: Option[ObjectId] = None,
  createdBy: ObjectId,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now(),
  maxParticipants: Option[Int] = None,
  registrationDeadline: Option[Instant] = None,
  rules: List[String] = List.empty,
  prizes: Option[Map[Int, String]] = None // Rank -> Prize description
)

object Contest {
  def getStatus(contest: Contest): ContestStatus.Value = {
    val now = Instant.now()
    if (now.isBefore(contest.startTime)) ContestStatus.Upcoming
    else if (now.isAfter(contest.endTime)) ContestStatus.Ended
    else ContestStatus.Running
  }
  
  def isRegistrationOpen(contest: Contest): Boolean = {
    val now = Instant.now()
    contest.registrationDeadline match {
      case Some(deadline) => now.isBefore(deadline)
      case None => now.isBefore(contest.startTime)
    }
  }
  
  def updateParticipantScore(
    contest: Contest,
    userId: ObjectId,
    problemId: ObjectId,
    points: Double
  ): Contest = {
    val participant = contest.participants.getOrElse(userId, Participant(userId))
    val updatedParticipant = participant.copy(
      score = participant.score + points,
      problemsSolved = participant.problemsSolved + 1,
      lastSubmission = Some(Instant.now())
    )
    
    contest.copy(
      participants = contest.participants + (userId -> updatedParticipant),
      updatedAt = Instant.now()
    )
  }
  
  def calculateRankings(contest: Contest): Contest = {
    val sortedParticipants = contest.participants.values.toList
      .sortBy(p => (-p.score, p.lastSubmission.getOrElse(Instant.now())))
    
    val rankedParticipants = sortedParticipants.zipWithIndex.map { case (participant, index) =>
      participant.userId -> participant.copy(rank = Some(index + 1))
    }.toMap
    
    contest.copy(
      participants = rankedParticipants,
      updatedAt = Instant.now()
    )
  }
} 