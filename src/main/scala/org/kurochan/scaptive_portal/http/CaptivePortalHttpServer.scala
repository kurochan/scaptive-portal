package org.kurochan.scaptive_portal.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

trait CaptivePortalHttpServer {
  def start(): Future[Unit]
  def shutdown(): Future[Unit]

}

class CaptivePortalHttpServerImpl(captivePortalRouter: CaptivePortalRouter)(implicit ec: ExecutionContext, system: ActorSystem)
  extends CaptivePortalHttpServer
  with StrictLogging {

  private var bindingFuture: Future[Http.ServerBinding] = _

  def start(): Future[Unit] = {

    implicit val mat = ActorMaterializer()

    bindingFuture = Http().bindAndHandle(
      captivePortalRouter.route,
      "0.0.0.0",
      8080
    )

    bindingFuture.map { _ =>
      logger.info(s"server started at http://0.0.0.0:8080/")
      ()
    }
  }

  def shutdown(): Future[Unit] = {

    bindingFuture.flatMap { _.unbind() }.map(_ => ())
  }
}
