package org.thp.thehive.connector.cortex.controllers.v0

import akka.stream.Materializer
import org.specs2.mock.Mockito
import org.specs2.specification.core.{Fragment, Fragments}
import org.thp.cortex.dto.v0.OutputReportTemplate
import org.thp.scalligraph.AppBuilder
import org.thp.scalligraph.auth.{AuthSrv, UserSrv}
import org.thp.scalligraph.controllers.{FakeTemporaryFile, TestAuthSrv}
import org.thp.scalligraph.models.{Database, DatabaseProviders, DummyUserSrv, Schema}
import org.thp.scalligraph.services.config.ConfigActor
import org.thp.scalligraph.services.{LocalFileSystemStorageSrv, StorageSrv}
import org.thp.thehive.connector.cortex.services.CortexActor
import org.thp.thehive.models.{DatabaseBuilder, Permissions, TheHiveSchema}
import org.thp.thehive.services.LocalUserSrv
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, MultipartFormData}
import play.api.test.{FakeRequest, NoMaterializer, PlaySpecification}
import play.api.{Configuration, Environment}

import scala.util.{Random, Try}

class ReportCtrlTest extends PlaySpecification with Mockito {
  val dummyUserSrv               = DummyUserSrv(permissions = Permissions.all)
  val config: Configuration      = Configuration.load(Environment.simple())
  implicit val mat: Materializer = NoMaterializer

  Fragments.foreach(new DatabaseProviders(config).list) { dbProvider =>
    val app: AppBuilder = AppBuilder()
      .bind[UserSrv, LocalUserSrv]
      .bindToProvider(dbProvider)
      .bind[AuthSrv, TestAuthSrv]
      .bind[StorageSrv, LocalFileSystemStorageSrv]
      .bind[Schema, TheHiveSchema]
      .bindActor[ConfigActor]("config-actor")
      .bindActor[CortexActor]("cortex-actor")
      .addConfiguration("play.modules.disabled = [org.thp.scalligraph.ScalligraphModule, org.thp.thehive.TheHiveModule]")

    step(setupDatabase(app)) ^ specs(dbProvider.name, app) ^ step(teardownDatabase(app))
  }

  def setupDatabase(app: AppBuilder): Try[Unit] =
    app.instanceOf[DatabaseBuilder].build()(app.instanceOf[Database], dummyUserSrv.getSystemAuthContext)

  def teardownDatabase(app: AppBuilder): Unit = app.instanceOf[Database].drop()

  def specs(name: String, app: AppBuilder): Fragment = {
    val reportCtrl: ReportCtrl = app.instanceOf[ReportCtrl]
    val cortexQueryExecutor    = app.instanceOf[CortexQueryExecutor]

    s"[$name] report controller" should {
//      "create, fetch, update and delete a template" in {
//
//      }

      "import valid templates contained in a zip file and fetch them by id and type" in {
//        val archive = SingletonTemporaryFileCreator.create(new File(getClass.getResource("/report-templates.zip").toURI).toPath)
        val file = FilePart("templates", "report-templates.zip", Option("application/zip"), FakeTemporaryFile.fromResource("/report-templates.zip"))
        val request = FakeRequest("POST", s"/api/connector/cortex/report/template/_import")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withBody(AnyContentAsMultipartFormData(MultipartFormData(Map.empty, Seq(file), Nil)))

        val result = reportCtrl.importTemplates(request)

        status(result) must equalTo(200).updateMessage(s => s"$s\n${contentAsString(result)}")

        val importedList = contentAsJson(result)

        importedList must equalTo(
          Json.obj(
            "JoeSandbox_File_Analysis_Noinet_2_0" -> true,
            "Yeti_1_0"                            -> true,
            "testAnalyzer_short"                  -> true
          )
        )

        val getRequest = FakeRequest("GET", s"/api/connector/cortex/report/template/content/JoeSandbox_File_Analysis_Noinet_2_0/long")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
        val getResult = reportCtrl.get("JoeSandbox_File_Analysis_Noinet_2_0")(getRequest)
        status(getResult) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(getResult)}")







        // FIXME some obscure db conflicts issue again

        val createRequest = FakeRequest("POST", "/api/connector/cortex/report/template")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                "analyzerId": "anaTest1",
                "content": "<span>${Random.alphanumeric.take(10).mkString}</span>"
              }
            """.stripMargin))
        val createResult = reportCtrl.create(createRequest)
        status(createResult) must beEqualTo(201).updateMessage(s => s"$s\n${contentAsString(createResult)}")

        val outputReportTemplate = contentAsJson(createResult).as[OutputReportTemplate]
        val getRequest2 = FakeRequest("GET", s"/api/connector/cortex/report/template/${outputReportTemplate.id}")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
        val getResult2 = reportCtrl.get(outputReportTemplate.id)(getRequest2)
        status(getResult2) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(getResult2)}")

        val updateRequest = FakeRequest("PATCH", s"/api/connector/cortex/report/template/${outputReportTemplate.id}")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse("""{"content": "<br/>"}"""))
        val updateResult = reportCtrl.update(outputReportTemplate.id)(updateRequest)
        status(updateResult) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(updateResult)}")
        contentAsJson(updateResult).as[OutputReportTemplate].content must equalTo("<br/>")

        val deleteRequest = FakeRequest("DELETE", s"/api/connector/cortex/report/template/${outputReportTemplate.id}")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
        val deleteResult = reportCtrl.delete(outputReportTemplate.id)(deleteRequest)
        status(deleteResult) must beEqualTo(204).updateMessage(s => s"$s\n${contentAsString(updateResult)}")
      }

      "search templates properly" in {
        val requestSearch = FakeRequest("POST", s"/api/connector/cortex/report/template/_search?range=0-200")
          .withHeaders("user" -> "user2", "X-Organisation" -> "default")
          .withJsonBody(Json.parse(s"""
              {
                 "query":{"analyzerId": "Yeti_1_0"}
              }
            """.stripMargin))
        val resultSearch = cortexQueryExecutor.report.search(requestSearch)

        status(resultSearch) must beEqualTo(200).updateMessage(s => s"$s\n${contentAsString(resultSearch)}")
      }
    }
  }
}
