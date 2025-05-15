package models

import org.mongodb.scala.bson.ObjectId
import java.time.Instant

case class TestResult(
  testCaseId: String,
  passed: Boolean,
  executionTime: Long, // in milliseconds
  memoryUsed: Long, // in bytes
  output: String,
  error: Option[String] = None
)

case class ExecutionMetrics(
  totalTime: Long, // in milliseconds
  maxMemoryUsed: Long, // in bytes
  cpuTime: Long, // in milliseconds
  compilationTime: Long // in milliseconds
)

object SubmissionStatus extends Enumeration {
  type SubmissionStatus = Value
  val Queued, Running, Completed, Failed, TimeLimitExceeded, 
      MemoryLimitExceeded, CompilationError, RuntimeError = Value
}

case class Submission(
  _id: ObjectId = new ObjectId(),
  userId: ObjectId,
  problemId: ObjectId,
  code: String,
  language: String,
  status: String,
  testResults: List[TestResult] = List.empty,
  metrics: Option[ExecutionMetrics] = None,
  score: Double = 0.0, // Percentage of test cases passed
  submittedAt: Instant = Instant.now(),
  completedAt: Option[Instant] = None,
  contestId: Option[ObjectId] = None, // If submitted as part of a contest
  isPublic: Boolean = true // Whether the submission is visible to other users
)

object Submission {
  def calculateScore(testResults: List[TestResult]): Double = {
    if (testResults.isEmpty) 0.0
    else {
      val passedTests = testResults.count(_.passed)
      (passedTests.toDouble / testResults.size) * 100
    }
  }
  
  def updateWithResults(
    submission: Submission,
    testResults: List[TestResult],
    metrics: ExecutionMetrics,
    status: SubmissionStatus.Value
  ): Submission = {
    submission.copy(
      testResults = testResults,
      metrics = Some(metrics),
      score = calculateScore(testResults),
      status = status.toString,
      completedAt = Some(Instant.now())
    )
  }
  
  def isSuccessful(submission: Submission): Boolean = {
    submission.status == SubmissionStatus.Completed.toString && 
    submission.score == 100.0
  }
} 