package services

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import models._
import org.mongodb.scala.bson.ObjectId
import scala.collection.mutable

@Singleton
class AnalyticsService @Inject()(
  mongoService: MongoService
)(implicit ec: ExecutionContext) {
  
  case class ProblemStats(
    totalAttempts: Int,
    successfulAttempts: Int,
    averageTime: Long,
    averageMemory: Long
  )
  
  case class UserPerformanceMetrics(
    problemsSolved: Int,
    totalAttempts: Int,
    successRate: Double,
    averageTimePerProblem: Long,
    averageMemoryUsage: Long,
    strongTags: List[(String, Double)], // (tag, success rate)
    weakTags: List[(String, Double)],
    recentProgress: List[(String, Int)] // (date, problems solved)
  )
  
  def analyzeUserPerformance(userId: ObjectId): Future[UserPerformanceMetrics] = {
    for {
      submissions <- mongoService.findUserSubmissions(userId)
      problems <- Future.sequence(
        submissions.map(s => mongoService.findProblemById(s.get("problemId").asObjectId().getValue))
      )
    } yield {
      val problemStats = mutable.Map[ObjectId, ProblemStats]()
      val tagStats = mutable.Map[String, (Int, Int)]() // (successful attempts, total attempts)
      
      submissions.zip(problems).foreach { case (submission, problemOpt) =>
        problemOpt.foreach { problem =>
          val problemId = problem.get("_id").asObjectId().getValue
          val isSuccessful = submission.get("status").asString().getValue == "Completed"
          val executionTime = submission.get("metrics")
            .asDocument()
            .get("totalTime")
            .asInt64()
            .getValue
          val memoryUsed = submission.get("metrics")
            .asDocument()
            .get("maxMemoryUsed")
            .asInt64()
            .getValue
          
          val stats = problemStats.getOrElse(problemId, ProblemStats(0, 0, 0, 0))
          problemStats(problemId) = stats.copy(
            totalAttempts = stats.totalAttempts + 1,
            successfulAttempts = stats.successfulAttempts + (if (isSuccessful) 1 else 0),
            averageTime = (stats.averageTime * stats.totalAttempts + executionTime) / (stats.totalAttempts + 1),
            averageMemory = (stats.averageMemory * stats.totalAttempts + memoryUsed) / (stats.totalAttempts + 1)
          )
          
          // Update tag statistics
          problem.get("tags").asArray().getValues.foreach { tag =>
            val tagName = tag.asString().getValue
            val (successful, total) = tagStats.getOrElse(tagName, (0, 0))
            tagStats(tagName) = (
              successful + (if (isSuccessful) 1 else 0),
              total + 1
            )
          }
        }
      }
      
      // Calculate tag success rates
      val tagSuccessRates = tagStats.map { case (tag, (successful, total)) =>
        (tag, successful.toDouble / total)
      }.toList
      
      // Sort tags by success rate
      val (strongTags, weakTags) = tagSuccessRates.partition(_._2 >= 0.6)
      
      // Calculate recent progress (last 30 days)
      val recentProgress = submissions
        .groupBy(s => s.get("submittedAt").asDateTime().getValue.toString.substring(0, 10))
        .map { case (date, submissions) =>
          (date, submissions.count(_.get("status").asString().getValue == "Completed"))
        }
        .toList
        .sortBy(_._1)
        .takeRight(30)
      
      UserPerformanceMetrics(
        problemsSolved = problemStats.count(_._2.successfulAttempts > 0),
        totalAttempts = submissions.size,
        successRate = problemStats.values.map(s => s.successfulAttempts.toDouble / s.totalAttempts).sum / problemStats.size,
        averageTimePerProblem = problemStats.values.map(_.averageTime).sum / problemStats.size,
        averageMemoryUsage = problemStats.values.map(_.averageMemory).sum / problemStats.size,
        strongTags = strongTags.sortBy(-_._2).take(5),
        weakTags = weakTags.sortBy(_._2).take(5),
        recentProgress = recentProgress
      )
    }
  }
  
  def generateUserInsights(metrics: UserPerformanceMetrics): List[String] = {
    val insights = mutable.ListBuffer[String]()
    
    // Overall performance insights
    insights += s"You have solved ${metrics.problemsSolved} problems with a ${metrics.successRate * 100}% success rate."
    
    // Strong areas
    if (metrics.strongTags.nonEmpty) {
      insights += "Your strongest areas are: " +
        metrics.strongTags.map { case (tag, rate) =>
          s"$tag (${(rate * 100).round}% success rate)"
        }.mkString(", ")
    }
    
    // Areas for improvement
    if (metrics.weakTags.nonEmpty) {
      insights += "Consider practicing more in: " +
        metrics.weakTags.map { case (tag, rate) =>
          s"$tag (${(rate * 100).round}% success rate)"
        }.mkString(", ")
    }
    
    // Recent progress
    val recentSolved = metrics.recentProgress.map(_._2).sum
    if (recentSolved > 0) {
      insights += s"You've solved $recentSolved problems in the last 30 days. Keep up the good work!"
    } else {
      insights += "Try to solve at least one problem per day to maintain consistency."
    }
    
    // Performance optimization suggestions
    if (metrics.averageTimePerProblem > 2000) { // 2 seconds
      insights += "Your solutions might benefit from optimization. Try to reduce time complexity in your algorithms."
    }
    
    if (metrics.averageMemoryUsage > 50 * 1024 * 1024) { // 50MB
      insights += "Consider optimizing memory usage in your solutions."
    }
    
    insights.toList
  }
  
  def getRecommendedProblems(userId: ObjectId): Future[List[Problem]] = {
    for {
      metrics <- analyzeUserPerformance(userId)
      // Find problems that target user's weak areas
      problems <- mongoService.findProblems(
        tags = Some(metrics.weakTags.map(_._1).take(2)),
        limit = 5
      )
    } yield {
      problems.map(_.asDocument()).toList.map { doc =>
        // Convert Document to Problem case class
        // This is a simplified version, you'll need to implement proper conversion
        Problem(
          _id = doc.get("_id").asObjectId().getValue,
          title = doc.get("title").asString().getValue,
          description = doc.get("description").asString().getValue,
          difficulty = doc.get("difficulty").asString().getValue,
          tags = doc.get("tags").asArray().getValues.map(_.asString().getValue).toSet,
          constraints = doc.get("constraints").asString().getValue,
          inputFormat = doc.get("inputFormat").asString().getValue,
          outputFormat = doc.get("outputFormat").asString().getValue,
          testCases = List(), // You'll need to implement proper conversion
          solutions = List(), // You'll need to implement proper conversion
          createdBy = doc.get("createdBy").asObjectId().getValue,
          timeLimit = doc.get("timeLimit").asInt32().getValue,
          memoryLimit = doc.get("memoryLimit").asInt32().getValue
        )
      }
    }
  }
} 