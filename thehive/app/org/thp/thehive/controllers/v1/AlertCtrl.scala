package org.thp.thehive.controllers.v1

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.RichOptionTry
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser, UpdateFieldsParser}
import org.thp.scalligraph.models.Database
import org.thp.thehive.dto.v1.InputAlert
import org.thp.thehive.models.Permissions
import org.thp.thehive.services.{AlertSrv, CaseTemplateSrv, UserSrv}

@Singleton
class AlertCtrl @Inject()(entryPoint: EntryPoint, db: Database, alertSrv: AlertSrv, caseTemplateSrv: CaseTemplateSrv, userSrv: UserSrv)
    extends AlertConversion
    with CaseConversion {

  def create: Action[AnyContent] =
    entryPoint("create alert")
      .extract('alert, FieldsParser[InputAlert])
      .extract('caseTemplate, FieldsParser[String].optional.on("caseTemplate"))
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          val caseTemplateName: Option[String] = request.body('caseTemplate)
          val inputAlert: InputAlert           = request.body('alert)
          for {
            caseTemplate ← caseTemplateName.map { ct ⇒
              caseTemplateSrv
                .get(ct)
                .available
                .richCaseTemplate
                .getOrFail()
            }.flip
            organisation ← userSrv.current.organisations(Permissions.manageAlert).getOrFail()
            customFields = inputAlert.customFieldValue.map(fromInputCustomField).toMap
            richAlert ← alertSrv.create(request.body('alert), organisation, customFields, caseTemplate)
          } yield Results.Created(richAlert.toJson)
        }
      }

  def get(alertId: String): Action[AnyContent] =
    entryPoint("get alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .visible
            .richAlert
            .getOrFail()
            .map(alert ⇒ Results.Ok(alert.toJson))
        }
      }

//  def list: Action[AnyContent] =
//    entryPoint("list alert")
//      .authenticated { implicit request ⇒
//        db.tryTransaction { implicit graph ⇒
//          val alerts = alertSrv.initSteps
//            .availableFor(request.organisation)
//            .richAlert
//            .map(_.toJson)
//            .toList()
//          Success(Results.Ok(Json.toJson(alerts)))
//        }
//      }

  def update(alertId: String): Action[AnyContent] =
    entryPoint("update alert")
      .extract('alert, UpdateFieldsParser[InputAlert])
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.update(alertId, request.body('alert))
              Results.NoContent
            }
        }
      }

  def mergeWithCase(alertId: String, caseId: String) = ???

  def markAsRead(alertId: String): Action[AnyContent] =
    entryPoint("mark alert as read")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.markAsRead(alertId)
              Results.NoContent
            }
        }
      }

  def markAsUnread(alertId: String): Action[AnyContent] =
    entryPoint("mark alert as unread")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.markAsUnread(alertId)
              Results.NoContent
            }
        }
      }

  def createCase(alertId: String): Action[AnyContent] =
    entryPoint("create case from alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          for {
            (alert, organisation) ← alertSrv.get(alertId).alertUserOrganisation(Permissions.manageCase).getOrFail()
            richCase              ← alertSrv.createCase(alert, None, organisation)
          } yield Results.Created(richCase.toJson)
        }
      }

  def followAlert(alertId: String): Action[AnyContent] =
    entryPoint("follow alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.followAlert(alertId)
              Results.NoContent
            }
        }
      }

  def unfollowAlert(alertId: String): Action[AnyContent] =
    entryPoint("unfollow alert")
      .authenticated { implicit request ⇒
        db.tryTransaction { implicit graph ⇒
          alertSrv
            .get(alertId)
            .can(Permissions.manageAlert)
            .existsOrFail()
            .map { _ ⇒
              alertSrv.unfollowAlert(alertId)
              Results.NoContent
            }
        }
      }
}