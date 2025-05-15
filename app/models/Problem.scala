package models

import org.mongodb.scala.bson.ObjectId
import java.time.Instant

case class TestCase(
  input: String,
  expectedOutput: String,
  isPublic: Boolean,
  explanation: Option[String] = None
)

case class Solution(
  code: String,
  language: String,
  timeComplexity: String,
  spaceComplexity: String,
  authorId: ObjectId,
  isOfficial: Boolean = false
)

case class Problem(
  _id: ObjectId = new ObjectId(),
  title: String,
  description: String,
  difficulty: String, // Easy, Medium, Hard
  tags: Set[String],
  constraints: String,
  inputFormat: String,
  outputFormat: String,
  testCases: List[TestCase],
  solutions: List[Solution],
  acceptanceRate: Double = 0.0,
  totalSubmissions: Int = 0,
  successfulSubmissions: Int = 0,
  createdAt: Instant = Instant.now(),
  updatedAt: Instant = Instant.now(),
  createdBy: ObjectId,
  timeLimit: Int, // in milliseconds
  memoryLimit: Int // in MB
)

object ProblemDifficulty extends Enumeration {
  type ProblemDifficulty = Value
  val Easy, Medium, Hard = Value
}

object Problem {
  def calculateAcceptanceRate(totalSubmissions: Int, successfulSubmissions: Int): Double = {
    if (totalSubmissions == 0) 0.0
    else (successfulSubmissions.toDouble / totalSubmissions) * 100
  }
  
  def updateSubmissionStats(problem: Problem, isSuccessful: Boolean): Problem = {
    val newTotalSubmissions = problem.totalSubmissions + 1
    val newSuccessfulSubmissions = if (isSuccessful) problem.successfulSubmissions + 1 
                                  else problem.successfulSubmissions
    
    problem.copy(
      totalSubmissions = newTotalSubmissions,
      successfulSubmissions = newSuccessfulSubmissions,
      acceptanceRate = calculateAcceptanceRate(newTotalSubmissions, newSuccessfulSubmissions),
      updatedAt = Instant.now()
    )
  }
} 