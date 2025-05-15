package services

import javax.inject.{Inject, Singleton}
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model.Indexes._
import scala.concurrent.{ExecutionContext, Future}
import models._
import play.api.Configuration
import org.mongodb.scala.bson.conversions.Bson
import java.util.concurrent.TimeUnit

@Singleton
class MongoService @Inject()(config: Configuration)(implicit ec: ExecutionContext) {
  private val mongoClient: MongoClient = MongoClient(config.get[String]("mongodb.uri"))
  private val database: MongoDatabase = mongoClient.getDatabase("competitive_platform")
  
  // Collections
  private val users = database.getCollection[Document]("users")
  private val problems = database.getCollection[Document]("problems")
  private val submissions = database.getCollection[Document]("submissions")
  private val contests = database.getCollection[Document]("contests")
  
  // Initialize indexes
  def initializeIndexes(): Future[Unit] = {
    for {
      // User indexes
      _ <- users.createIndex(ascending("username")).toFuture()
      _ <- users.createIndex(ascending("email")).toFuture()
      
      // Problem indexes
      _ <- problems.createIndex(ascending("difficulty")).toFuture()
      _ <- problems.createIndex(ascending("tags")).toFuture()
      _ <- problems.createIndex(ascending("acceptanceRate")).toFuture()
      
      // Submission indexes
      _ <- submissions.createIndex(ascending("userId")).toFuture()
      _ <- submissions.createIndex(ascending("problemId")).toFuture()
      _ <- submissions.createIndex(ascending("submittedAt")).toFuture()
      
      // Contest indexes
      _ <- contests.createIndex(ascending("startTime")).toFuture()
      _ <- contests.createIndex(ascending("endTime")).toFuture()
    } yield ()
  }
  
  // User operations
  def findUserById(id: ObjectId): Future[Option[Document]] = {
    users.find(equal("_id", id)).headOption()
  }
  
  def findUserByUsername(username: String): Future[Option[Document]] = {
    users.find(equal("username", username)).headOption()
  }
  
  def insertUser(user: Document): Future[InsertOneResult] = {
    users.insertOne(user)
  }
  
  def updateUser(id: ObjectId, update: Bson): Future[UpdateResult] = {
    users.updateOne(equal("_id", id), update)
  }
  
  // Problem operations
  def findProblemById(id: ObjectId): Future[Option[Document]] = {
    problems.find(equal("_id", id)).headOption()
  }
  
  def findProblems(
    difficulty: Option[String] = None,
    tags: Option[List[String]] = None,
    skip: Int = 0,
    limit: Int = 20
  ): Future[Seq[Document]] = {
    var filter = Document()
    
    difficulty.foreach(d => filter = filter.append("difficulty", d))
    tags.foreach(t => filter = filter.append("tags", Document("$all" -> t)))
    
    problems
      .find(filter)
      .sort(Document("acceptanceRate" -> -1))
      .skip(skip)
      .limit(limit)
      .toFuture()
  }
  
  def insertProblem(problem: Document): Future[InsertOneResult] = {
    problems.insertOne(problem)
  }
  
  def updateProblem(id: ObjectId, update: Bson): Future[UpdateResult] = {
    problems.updateOne(equal("_id", id), update)
  }
  
  // Submission operations
  def findSubmissionById(id: ObjectId): Future[Option[Document]] = {
    submissions.find(equal("_id", id)).headOption()
  }
  
  def findUserSubmissions(
    userId: ObjectId,
    problemId: Option[ObjectId] = None,
    skip: Int = 0,
    limit: Int = 20
  ): Future[Seq[Document]] = {
    var filter = Document("userId" -> userId)
    problemId.foreach(pid => filter = filter.append("problemId", pid))
    
    submissions
      .find(filter)
      .sort(Document("submittedAt" -> -1))
      .skip(skip)
      .limit(limit)
      .toFuture()
  }
  
  def insertSubmission(submission: Document): Future[InsertOneResult] = {
    submissions.insertOne(submission)
  }
  
  def updateSubmission(id: ObjectId, update: Bson): Future[UpdateResult] = {
    submissions.updateOne(equal("_id", id), update)
  }
  
  // Contest operations
  def findContestById(id: ObjectId): Future[Option[Document]] = {
    contests.find(equal("_id", id)).headOption()
  }
  
  def findActiveContests(now: Long): Future[Seq[Document]] = {
    contests
      .find(
        and(
          lte("startTime", now),
          gte("endTime", now)
        )
      )
      .sort(Document("startTime" -> 1))
      .toFuture()
  }
  
  def insertContest(contest: Document): Future[InsertOneResult] = {
    contests.insertOne(contest)
  }
  
  def updateContest(id: ObjectId, update: Bson): Future[UpdateResult] = {
    contests.updateOne(equal("_id", id), update)
  }
  
  // Cleanup
  def close(): Unit = {
    mongoClient.close()
  }
} 