package org.thp.thehive.controllers.v0

import scala.util.{Failure, Success}

import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.AuthorizationError
import org.thp.scalligraph.controllers._
import org.thp.scalligraph.models.{Database, ResultWithTotalSize}
import org.thp.scalligraph.query.Query
import org.thp.thehive.dto.v0.InputTask
import org.thp.thehive.services.{CaseSrv, TaskSrv}

@Singleton
class TaskCtrl @Inject()(entryPoint: EntryPoint, db: Database, taskSrv: TaskSrv, caseSrv: CaseSrv, val queryExecutor: TheHiveQueryExecutor)
    extends QueryCtrl
    with TaskConversion {

  lazy val logger = Logger(getClass)

  def create: Action[AnyContent] =
    entryPoint("create task")
      .extract('task, FieldsParser[InputTask])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val inputTask: InputTask = request.body('task)
          caseSrv.getOrFail(inputTask.caseId).map { `case` ⇒
            val createdTask = taskSrv.create(inputTask, `case`)
            Results.Created(createdTask.toJson)
          }
        }
      }

  def get(taskId: String): Action[AnyContent] =
    entryPoint("get task")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          taskSrv
            .get(taskId)
            .availableFor(request.organisation)
            .getOrFail()
            .map { task ⇒
              Results.Ok(task.toJson)
            }
        }
      }

  def list: Action[AnyContent] =
    entryPoint("list task")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val tasks = taskSrv.initSteps
            .availableFor(request.organisation)
            .toList()
            .map(_.toJson)
          Success(Results.Ok(Json.toJson(tasks)))
        }
      }

  def update(taskId: String): Action[AnyContent] =
    entryPoint("update task")
      .extract('task, UpdateFieldsParser[InputTask])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          if (taskSrv.isAvailableFor(taskId)) {
            taskSrv.update(taskId, outputTaskProperties(db), request.body('task))
            Success(Results.NoContent)
          } else Failure(AuthorizationError(s"Task $taskId doesn't exist or permission is insufficient"))
        }
      }

  def stats(): Action[AnyContent] = {
    val parser: FieldsParser[Seq[Query]] = statsParser("listTask")
    entryPoint("stats task")
      .extract('query, parser)
      .authenticated { implicit request ⇒
        val queries: Seq[Query] = request.body('query)
        val results = queries
          .map { query ⇒
            db.transaction { graph ⇒
              queryExecutor.execute(query, graph, request.authContext).toJson
            }
          }
          .foldLeft(JsObject.empty) {
            case (acc, o: JsObject) ⇒ acc ++ o
            case (acc, r) ⇒
              logger.warn(s"Invalid stats result: $r")
              acc
          }
        Success(Results.Ok(results))
      }
  }

  def search: Action[AnyContent] =
    entryPoint("search case")
      .extract('query, searchParser("listTask", paged = false))
      .authenticated { implicit request ⇒
        val query: Query = request.body('query)
        val result = db.transaction { graph ⇒
          queryExecutor.execute(query, graph, request.authContext)
        }
        val resp = Results.Ok((result.toJson \ "result").as[JsValue])
        result.toOutput match {
          case ResultWithTotalSize(_, size) ⇒ Success(resp.withHeaders("X-Total" → size.toString))
          case _                            ⇒ Success(resp)
        }
      }
}