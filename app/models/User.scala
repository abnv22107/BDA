package models

import org.mongodb.scala.bson.ObjectId
import java.time.Instant

case class UserStats(
  totalSolved: Int = 0,
  easyProblemsSolved: Int = 0,
  mediumProblemsSolved: Int = 0,
  hardProblemsSolved: Int = 0,
  averageAttempts: Double = 0.0,
  averageTimePerProblem: Long = 0, // in milliseconds
  strongTags: Set[String] = Set.empty,
  weakTags: Set[String] = Set.empty
)

case class Achievement(
  id: String,
  name: String,
  description: String,
  unlockedAt: Instant,
  category: String // e.g., "Problem Solving", "Contest", "Social"
)

case class ContestParticipation(
  contestId: ObjectId,
  rank: Int,
  score: Double,
  problemsSolved: Int,
  participatedAt: Instant
)

case class User(
  _id: ObjectId = new ObjectId(),
  username: String,
  email: String,
  passwordHash: String,
  fullName: Option[String] = None,
  bio: Option[String] = None,
  country: Option[String] = None,
  organization: Option[String] = None,
  role: String = "USER", // USER, ADMIN, MODERATOR
  stats: UserStats = UserStats(),
  achievements: Set[Achievement] = Set.empty,
  contestHistory: List[ContestParticipation] = List.empty,
  following: Set[ObjectId] = Set.empty,
  followers: Set[ObjectId] = Set.empty,
  createdAt: Instant = Instant.now(),
  lastActive: Instant = Instant.now(),
  isVerified: Boolean = false,
  isBanned: Boolean = false
)

object UserRole extends Enumeration {
  type UserRole = Value
  val User, Moderator, Admin = Value
}

object User {
  def updateStats(user: User, problem: Problem, attemptCount: Int, solveTimeMs: Long): User = {
    val stats = user.stats
    val newStats = stats.copy(
      totalSolved = stats.totalSolved + 1,
      easyProblemsSolved = if (problem.difficulty == "Easy") stats.easyProblemsSolved + 1 else stats.easyProblemsSolved,
      mediumProblemsSolved = if (problem.difficulty == "Medium") stats.mediumProblemsSolved + 1 else stats.mediumProblemsSolved,
      hardProblemsSolved = if (problem.difficulty == "Hard") stats.hardProblemsSolved + 1 else stats.hardProblemsSolved,
      averageAttempts = ((stats.averageAttempts * stats.totalSolved) + attemptCount) / (stats.totalSolved + 1),
      averageTimePerProblem = ((stats.averageTimePerProblem * stats.totalSolved) + solveTimeMs) / (stats.totalSolved + 1)
    )
    
    user.copy(
      stats = newStats,
      lastActive = Instant.now()
    )
  }
  
  def addAchievement(user: User, achievement: Achievement): User = {
    user.copy(
      achievements = user.achievements + achievement
    )
  }
} 