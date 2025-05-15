package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import services._
import models._
import org.mongodb.scala.bson.ObjectId

@Singleton
class AnalyticsController @Inject()(
  val controllerComponents: ControllerComponents,
  analyticsService: AnalyticsService
)(implicit ec: ExecutionContext) extends BaseController {
  
  // JSON formatters
  implicit val objectIdFormat: Format[ObjectId] = new Format[ObjectId] {
    def reads(json: JsValue): JsResult[ObjectId] = json match {
      case JsString(s) if ObjectId.isValid(s) => JsSuccess(new ObjectId(s))
      case _ => JsError("Invalid ObjectId")
    }
    def writes(o: ObjectId): JsValue = JsString(o.toString)
  }
  
  implicit val userPerformanceMetricsWrites: Writes[UserPerformanceMetrics] = new Writes[UserPerformanceMetrics] {
    def writes(metrics: UserPerformanceMetrics): JsValue = Json.obj(
      "problemsSolved" -> metrics.problemsSolved,
      "totalAttempts" -> metrics.totalAttempts,
      "successRate" -> metrics.successRate,
      "averageTimePerProblem" -> metrics.averageTimePerProblem,
      "averageMemoryUsage" -> metrics.averageMemoryUsage,
      "strongTags" -> metrics.strongTags.map { case (tag, rate) =>
        Json.obj("tag" -> tag, "successRate" -> rate)
      },
      "weakTags" -> metrics.weakTags.map { case (tag, rate) =>
        Json.obj("tag" -> tag, "successRate" -> rate)
      },
      "recentProgress" -> metrics.recentProgress.map { case (date, count) =>
        Json.obj("date" -> date, "problemsSolved" -> count)
      }
    )
  }
  
  implicit val problemWrites: Writes[Problem] = Json.writes[Problem]
  
  def getUserPerformance(id: String): Action[AnyContent] = Action.async { _ =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid user ID")))
    } else {
      analyticsService.analyzeUserPerformance(new ObjectId(id)).map { metrics =>
        Ok(Json.toJson(metrics))
      }.recover {
        case e: Exception =>
          InternalServerError(Json.obj("message" -> e.getMessage))
      }
    }
  }
  
  def getUserInsights(id: String): Action[AnyContent] = Action.async { _ =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid user ID")))
    } else {
      analyticsService.analyzeUserPerformance(new ObjectId(id)).map { metrics =>
        val insights = analyticsService.generateUserInsights(metrics)
        Ok(Json.obj("insights" -> insights))
      }.recover {
        case e: Exception =>
          InternalServerError(Json.obj("message" -> e.getMessage))
      }
    }
  }
  
  def getRecommendedProblems(id: String): Action[AnyContent] = Action.async { _ =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid user ID")))
    } else {
      analyticsService.getRecommendedProblems(new ObjectId(id)).map { problems =>
        Ok(Json.toJson(problems))
      }.recover {
        case e: Exception =>
          InternalServerError(Json.obj("message" -> e.getMessage))
      }
    }
  }
} 