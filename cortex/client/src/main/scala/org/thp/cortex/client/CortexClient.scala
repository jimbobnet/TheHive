package org.thp.cortex.client

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

import play.api.Logger

import akka.actor.ActorSystem
import org.thp.cortex.dto.client.{InputCortexAnalyzer, OutputCortexAnalyzer}
import org.thp.cortex.dto.v0._
import org.thp.scalligraph.{DelayRetry, Retry}

class CortexClient(val name: String, baseUrl: String, refreshDelay: FiniteDuration, maxRetryOnError: Int)(
    implicit ws: CustomWSAPI,
    auth: Authentication,
    system: ActorSystem,
    ec: ExecutionContext
) {
  lazy val job            = new BaseClient[InputArtifact, OutputJob](s"$baseUrl/api/job")
  lazy val analyser       = new BaseClient[InputCortexAnalyzer, OutputCortexAnalyzer](s"$baseUrl/api/analyzer")
  lazy val logger         = Logger(getClass)
  val retrier: DelayRetry = Retry(maxRetryOnError).delayed(refreshDelay)(system.scheduler, ec)

  /**
    * GET analysers endpoint
    *
    * @return
    */
  def listAnalyser: Future[Seq[OutputCortexAnalyzer]] = analyser.list
}
