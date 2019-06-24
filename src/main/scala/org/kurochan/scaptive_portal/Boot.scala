package org.kurochan.scaptive_portal

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import org.kurochan.scaptive_portal.controller.{ControllerStreamConfigImpl, ControllerStreamImpl, OpenFlowControllerImpl}
import org.kurochan.scaptive_portal.http.service.{CaptivePortalServiceConfigImpl, CaptivePortalServiceImpl}
import org.kurochan.scaptive_portal.http.{CaptivePortalHttpServerImpl, CaptivePortalRouterImpl}
import org.kurochan.scaptive_portal.openflow.service.{DataPathManageService, DataPathManageServiceConfigImpl, DataPathManageServiceImpl, MessageSendServiceImpl}
import org.kurochan.scaptive_portal.server.{OpenFlowControllerServerImpl, OpenFlowControllerServiceConfigImpl}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}

object Boot extends App with StrictLogging {

  DogStatsD.disable

  implicit val system = ActorSystem("openflow-controller-system")
  Executors.currentActorSystem = system
  implicit val ec = Executors.service

  val dataPathManageService: DataPathManageService =
    new DataPathManageServiceImpl(DataPathManageServiceConfigImpl())

  val controllerStream = new ControllerStreamImpl(
    ControllerStreamConfigImpl(
      dataPathManageService,
      new MessageSendServiceImpl(dataPathManageService)
    )
  )
  val openFlowController = new OpenFlowControllerImpl(controllerStream)

  val (queue, _) = openFlowController.start()

  val openFlowControllerServerConfig = OpenFlowControllerServiceConfigImpl(queue, dataPathManageService)
  val openFlowControllerServer = new OpenFlowControllerServerImpl(openFlowControllerServerConfig)
  openFlowControllerServer.start()

  val captivePortalHttpServer = new CaptivePortalHttpServerImpl(new CaptivePortalRouterImpl(new CaptivePortalServiceImpl(new CaptivePortalServiceConfigImpl(dataPathManageService, queue))))
  captivePortalHttpServer.start()

  val f = Promise[Int].future
  Await.result(f, Duration.Inf)

  logger.info("############ hit Enter key to exit ##############")
  scala.io.StdIn.readLine()
  logger.info("############ CLOSE ##############")
  val future1 = openFlowControllerServer.shutdown()
  val future2 = captivePortalHttpServer.shutdown()
  val future3 = openFlowController.shutdown()

  val future = Future.sequence(Seq(future1, future2, future3))
  Await.result(future, Duration.Inf)
  Await.result(system.terminate(), Duration.Inf)
  logger.info("############ END ##############")
}
