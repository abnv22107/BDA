package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import services._
import models._
import org.mongodb.scala.bson.ObjectId
import java.time.Instant
import play.api.libs.functional.syntax._

@Singleton
class ContestController @Inject()(
  val controllerComponents: ControllerComponents,
  contestService: ContestService,
  mongoService: MongoService
)(implicit ec: ExecutionContext) extends BaseController {
  
  // JSON formatters
  implicit val objectIdFormat: Format[ObjectId] = new Format[ObjectId] {
    def reads(json: JsValue): JsResult[ObjectId] = json match {
      case JsString(s) if ObjectId.isValid(s) => JsSuccess(new ObjectId(s))
      case _ => JsError("Invalid ObjectId")
    }
    def writes(o: ObjectId): JsValue = JsString(o.toString)
  }
  
  implicit val contestProblemFormat: Format[ContestProblem] = Json.format[ContestProblem]
  implicit val participantFormat: Format[Participant] = Json.format[Participant]
  
  implicit val contestFormat: Format[Contest] = (
    (__ \ "_id").format[ObjectId] and
    (__ \ "title").format[String] and
    (__ \ "description").format[String] and
    (__ \ "startTime").format[Instant] and
    (__ \ "endTime").format[Instant] and
    (__ \ "problems").format[List[ContestProblem]] and
    (__ \ "participants").format[Map[ObjectId, Participant]] and
    (__ \ "visibility").format[String] and
    (__ \ "organizationId").formatNullable[ObjectId] and
    (__ \ "createdBy").format[ObjectId] and
    (__ \ "createdAt").format[Instant] and
    (__ \ "updatedAt").format[Instant] and
    (__ \ "maxParticipants").formatNullable[Int] and
    (__ \ "registrationDeadline").formatNullable[Instant] and
    (__ \ "rules").format[List[String]] and
    (__ \ "prizes").formatNullable[Map[Int, String]]
  )(Contest.apply, unlift(Contest.unapply))
  
  // Request models
  case class CreateContestRequest(
    title: String,
    description: String,
    startTime: Instant,
    endTime: Instant,
    problems: List[ContestProblem],
    visibility: String,
    organizationId: Option[ObjectId],
    maxParticipants: Option[Int],
    registrationDeadline: Option[Instant],
    rules: List[String],
    prizes: Option[Map[Int, String]]
  )
  
  implicit val createContestRequestFormat: Format[CreateContestRequest] = Json.format[CreateContestRequest]
  
  // Endpoints
  def createContest: Action[JsValue] = Action(parse.json).async { request =>
    request.body.validate[CreateContestRequest].fold(
      errors => Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors)))),
      contestRequest => {
        val userId = request.attrs.get(Security.USER_ID_KEY)
          .map(new ObjectId(_))
          .getOrElse(throw new IllegalStateException("User not authenticated"))
        
        val contest = Contest(
          title = contestRequest.title,
          description = contestRequest.description,
          startTime = contestRequest.startTime,
          endTime = contestRequest.endTime,
          problems = contestRequest.problems,
          visibility = contestRequest.visibility,
          organizationId = contestRequest.organizationId,
          createdBy = userId,
          maxParticipants = contestRequest.maxParticipants,
          registrationDeadline = contestRequest.registrationDeadline,
          rules = contestRequest.rules,
          prizes = contestRequest.prizes
        )
        
        contestService.createContest(contest)
          .map(id => Created(Json.obj("id" -> id.toString)))
          .recover { case e =>
            InternalServerError(Json.obj("message" -> e.getMessage))
          }
      }
    )
  }
  
  def getContest(id: String): Action[AnyContent] = Action.async { _ =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid contest ID")))
    } else {
      mongoService.findContestById(new ObjectId(id)).map {
        case Some(doc) => Ok(Json.toJson(doc))
        case None => NotFound(Json.obj("message" -> "Contest not found"))
      }
    }
  }
  
  def registerForContest(id: String): Action[AnyContent] = Action.async { request =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid contest ID")))
    } else {
      val userId = request.attrs.get(Security.USER_ID_KEY)
        .map(new ObjectId(_))
        .getOrElse(throw new IllegalStateException("User not authenticated"))
      
      contestService.registerParticipant(new ObjectId(id), userId).map {
        case true => Ok(Json.obj("message" -> "Successfully registered for contest"))
        case false => BadRequest(Json.obj("message" -> "Registration failed"))
      }
    }
  }
  
  def submitContestSolution(contestId: String, problemId: String): Action[JsValue] = Action(parse.json).async { request =>
    if (!ObjectId.isValid(contestId) || !ObjectId.isValid(problemId)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid contest or problem ID")))
    } else {
      request.body.validate[SubmitSolutionRequest].fold(
        errors => Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors)))),
        submission => {
          val userId = request.attrs.get(Security.USER_ID_KEY)
            .map(new ObjectId(_))
            .getOrElse(throw new IllegalStateException("User not authenticated"))
          
          val submissionObj = Submission(
            userId = userId,
            problemId = new ObjectId(problemId),
            code = submission.code,
            language = submission.language,
            status = "Queued",
            contestId = Some(new ObjectId(contestId))
          )
          
          contestService.submitSolution(
            new ObjectId(contestId),
            userId,
            new ObjectId(problemId),
            submissionObj
          ).map {
            case true => Ok(Json.obj("message" -> "Solution submitted successfully"))
            case false => BadRequest(Json.obj("message" -> "Submission failed"))
          }
        }
      )
    }
  }
  
  def getContestLeaderboard(id: String): Action[AnyContent] = Action.async { _ =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid contest ID")))
    } else {
      contestService.getContestLeaderboard(new ObjectId(id)).map { leaderboard =>
        Ok(Json.toJson(leaderboard.map { case (username, score, rank) =>
          Json.obj(
            "username" -> username,
            "score" -> score,
            "rank" -> rank
          )
        }))
      }
    }
  }
  
  def getActiveContests: Action[AnyContent] = Action.async { _ =>
    mongoService.findActiveContests(Instant.now().toEpochMilli).map { contests =>
      Ok(Json.toJson(contests))
    }
  }
} 