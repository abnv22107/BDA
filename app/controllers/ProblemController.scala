package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import services._
import models._
import org.mongodb.scala.bson.ObjectId
import play.api.libs.functional.syntax._

@Singleton
class ProblemController @Inject()(
  val controllerComponents: ControllerComponents,
  mongoService: MongoService,
  codeExecutionService: CodeExecutionService
)(implicit ec: ExecutionContext) extends BaseController {
  
  // JSON formatters
  implicit val objectIdFormat: Format[ObjectId] = new Format[ObjectId] {
    def reads(json: JsValue): JsResult[ObjectId] = json match {
      case JsString(s) if ObjectId.isValid(s) => JsSuccess(new ObjectId(s))
      case _ => JsError("Invalid ObjectId")
    }
    def writes(o: ObjectId): JsValue = JsString(o.toString)
  }
  
  implicit val testCaseFormat: Format[TestCase] = Json.format[TestCase]
  implicit val solutionFormat: Format[Solution] = Json.format[Solution]
  
  implicit val problemFormat: Format[Problem] = (
    (__ \ "_id").format[ObjectId] and
    (__ \ "title").format[String] and
    (__ \ "description").format[String] and
    (__ \ "difficulty").format[String] and
    (__ \ "tags").format[Set[String]] and
    (__ \ "constraints").format[String] and
    (__ \ "inputFormat").format[String] and
    (__ \ "outputFormat").format[String] and
    (__ \ "testCases").format[List[TestCase]] and
    (__ \ "solutions").format[List[Solution]] and
    (__ \ "acceptanceRate").format[Double] and
    (__ \ "totalSubmissions").format[Int] and
    (__ \ "successfulSubmissions").format[Int] and
    (__ \ "createdAt").format[java.time.Instant] and
    (__ \ "updatedAt").format[java.time.Instant] and
    (__ \ "createdBy").format[ObjectId] and
    (__ \ "timeLimit").format[Int] and
    (__ \ "memoryLimit").format[Int]
  )(Problem.apply, unlift(Problem.unapply))
  
  // Request models
  case class CreateProblemRequest(
    title: String,
    description: String,
    difficulty: String,
    tags: Set[String],
    constraints: String,
    inputFormat: String,
    outputFormat: String,
    testCases: List[TestCase],
    timeLimit: Int,
    memoryLimit: Int
  )
  
  implicit val createProblemRequestFormat: Format[CreateProblemRequest] = Json.format[CreateProblemRequest]
  
  case class SubmitSolutionRequest(
    code: String,
    language: String
  )
  
  implicit val submitSolutionRequestFormat: Format[SubmitSolutionRequest] = Json.format[SubmitSolutionRequest]
  
  // Endpoints
  def createProblem: Action[JsValue] = Action(parse.json).async { request =>
    request.body.validate[CreateProblemRequest].fold(
      errors => Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors)))),
      problemRequest => {
        val problem = Problem(
          title = problemRequest.title,
          description = problemRequest.description,
          difficulty = problemRequest.difficulty,
          tags = problemRequest.tags,
          constraints = problemRequest.constraints,
          inputFormat = problemRequest.inputFormat,
          outputFormat = problemRequest.outputFormat,
          testCases = problemRequest.testCases,
          solutions = List.empty,
          createdBy = request.attrs.get(Security.USER_ID_KEY)
            .map(new ObjectId(_))
            .getOrElse(throw new IllegalStateException("User not authenticated")),
          timeLimit = problemRequest.timeLimit,
          memoryLimit = problemRequest.memoryLimit
        )
        
        mongoService.insertProblem(Document(Json.toJson(problem)))
          .map(_ => Created(Json.toJson(problem)))
          .recover { case e =>
            InternalServerError(Json.obj("message" -> e.getMessage))
          }
      }
    )
  }
  
  def getProblem(id: String): Action[AnyContent] = Action.async { _ =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid problem ID")))
    } else {
      mongoService.findProblemById(new ObjectId(id)).map {
        case Some(doc) => Ok(Json.toJson(doc))
        case None => NotFound(Json.obj("message" -> "Problem not found"))
      }
    }
  }
  
  def listProblems(
    difficulty: Option[String],
    tags: Option[String],
    page: Int = 1,
    pageSize: Int = 20
  ): Action[AnyContent] = Action.async { _ =>
    val tagsList = tags.map(_.split(",").toList)
    mongoService.findProblems(
      difficulty = difficulty,
      tags = tagsList,
      skip = (page - 1) * pageSize,
      limit = pageSize
    ).map { problems =>
      Ok(Json.toJson(problems))
    }
  }
  
  def submitSolution(problemId: String): Action[JsValue] = Action(parse.json).async { request =>
    if (!ObjectId.isValid(problemId)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid problem ID")))
    } else {
      request.body.validate[SubmitSolutionRequest].fold(
        errors => Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors)))),
        submission => {
          val userId = request.attrs.get(Security.USER_ID_KEY)
            .map(new ObjectId(_))
            .getOrElse(throw new IllegalStateException("User not authenticated"))
          
          for {
            problemOpt <- mongoService.findProblemById(new ObjectId(problemId))
            result <- problemOpt match {
              case Some(problem) =>
                codeExecutionService.executeCode(
                  submission.code,
                  submission.language,
                  problem.get("testCases").asArray().getValues.map { testCase =>
                    val tc = testCase.asDocument()
                    TestCase(
                      input = tc.get("input").asString().getValue,
                      expectedOutput = tc.get("expectedOutput").asString().getValue,
                      isPublic = tc.get("isPublic").asBoolean().getValue,
                      explanation = tc.get("explanation").map(_.asString().getValue)
                    )
                  }.toList
                ).flatMap { testResults =>
                  val submissionDoc = Document(Json.toJson(Submission(
                    userId = userId,
                    problemId = new ObjectId(problemId),
                    code = submission.code,
                    language = submission.language,
                    status = if (testResults.forall(_.passed)) "Completed" else "Failed",
                    testResults = testResults,
                    metrics = Some(ExecutionMetrics(
                      totalTime = testResults.map(_.executionTime).sum,
                      maxMemoryUsed = testResults.map(_.memoryUsed).max,
                      cpuTime = 0, // TODO: Implement CPU time tracking
                      compilationTime = 0 // TODO: Implement compilation time tracking
                    ))
                  )))
                  
                  mongoService.insertSubmission(submissionDoc)
                    .map(_ => Ok(Json.toJson(testResults)))
                }
              case None =>
                Future.successful(NotFound(Json.obj("message" -> "Problem not found")))
            }
          } yield result
        }
      )
    }
  }
  
  def updateProblem(id: String): Action[JsValue] = Action(parse.json).async { request =>
    if (!ObjectId.isValid(id)) {
      Future.successful(BadRequest(Json.obj("message" -> "Invalid problem ID")))
    } else {
      request.body.validate[Problem].fold(
        errors => Future.successful(BadRequest(Json.obj("message" -> JsError.toJson(errors)))),
        problem => {
          val userId = request.attrs.get(Security.USER_ID_KEY)
            .map(new ObjectId(_))
            .getOrElse(throw new IllegalStateException("User not authenticated"))
          
          mongoService.findProblemById(new ObjectId(id)).flatMap {
            case Some(existingProblem) if existingProblem.get("createdBy").asObjectId().getValue == userId =>
              mongoService.updateProblem(new ObjectId(id), Document(Json.toJson(problem)))
                .map(_ => Ok(Json.toJson(problem)))
            case Some(_) =>
              Future.successful(Forbidden(Json.obj("message" -> "Not authorized to update this problem")))
            case None =>
              Future.successful(NotFound(Json.obj("message" -> "Problem not found")))
          }
        }
      )
    }
  }
} 