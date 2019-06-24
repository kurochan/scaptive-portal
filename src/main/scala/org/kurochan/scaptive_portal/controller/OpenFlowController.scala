package org.kurochan.scaptive_portal.controller

import akka.actor.ActorSystem
import akka.stream.scaladsl.SourceQueue
import akka.stream.{ActorMaterializer, KillSwitches, SharedKillSwitch}
import com.typesafe.scalalogging.LazyLogging
import org.kurochan.scaptive_portal.controller.model.ControllerMessage

import scala.concurrent.{ExecutionContext, Future}

trait OpenFlowController {

  def start(): (SourceQueue[ControllerMessage], Future[Unit])
  def shutdown(): Future[Unit]
}

class OpenFlowControllerImpl(controllerStream: ControllerStream)(implicit ec: ExecutionContext, system: ActorSystem)
  extends OpenFlowController
  with LazyLogging {

  private implicit val mat = ActorMaterializer()
  private implicit val killSwitch: SharedKillSwitch = KillSwitches.shared("kill-switch")

  private var shutdownFuture: Future[Unit] = Future.successful(())

  def start(): (SourceQueue[ControllerMessage], Future[Unit]) = {

    val (queue, future) = controllerStream.stream()

    shutdownFuture = future.map { _ =>
      mat.shutdown()
      ()
    }
    (queue, shutdownFuture)
  }

  def shutdown(): Future[Unit] = {
    killSwitch.shutdown()
    shutdownFuture
  }
}
