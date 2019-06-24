package org.kurochan.scaptive_portal.server

import akka.stream.scaladsl.SourceQueue
import org.kurochan.scaptive_portal.Executors
import org.kurochan.scaptive_portal.controller.model.ControllerMessage
import org.kurochan.scaptive_portal.openflow.handler.{ControllerServerHandler, MessageDecoder}
import org.kurochan.scaptive_portal.openflow.service.{DataPathManageService, MessageReceiveService, MessageReceiveServiceImpl}

import scala.concurrent.ExecutionContextExecutor

trait OpenFlowControllerServerConfig {

  val port: Int
  val executionContext: ExecutionContextExecutor
  val messageReceiveService: MessageReceiveService
  val controllerServerHandler: ControllerServerHandler
  def messageDecoder: MessageDecoder
}

case class OpenFlowControllerServiceConfigImpl(messageQueue: SourceQueue[ControllerMessage], dataPathManageService: DataPathManageService)
  extends OpenFlowControllerServerConfig {

  val port: Int = 6653
  val executionContext: ExecutionContextExecutor = Executors.service
  val messageReceiveService: MessageReceiveService = new MessageReceiveServiceImpl(dataPathManageService, messageQueue)
  val controllerServerHandler: ControllerServerHandler = new ControllerServerHandler(dataPathManageService, messageReceiveService)
  def messageDecoder: MessageDecoder = new MessageDecoder()
}
