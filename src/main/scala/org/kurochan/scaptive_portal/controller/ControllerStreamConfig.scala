package org.kurochan.scaptive_portal.controller

import akka.stream.scaladsl.{Flow, Sink, Source, SourceQueueWithComplete}
import akka.{Done, NotUsed}
import org.kurochan.scaptive_portal.Executors
import org.kurochan.scaptive_portal.controller.flow.{ControllerMessageSink, ControllerMessageSource, MessageProcessingFlow}
import org.kurochan.scaptive_portal.controller.model.{ControllerMessage, ServerMessage}
import org.kurochan.scaptive_portal.controller.service.{
  CaptivePortalServiceConfigImpl,
  CaptivePortalServiceImpl,
  MessageProcessingService,
  MessageProcessingServiceImpl
}
import org.kurochan.scaptive_portal.openflow.service.{DataPathManageService, MessageSendService}

import scala.concurrent.{ExecutionContext, Future}

trait ControllerStreamConfig {

  val messageSource: Source[ControllerMessage, SourceQueueWithComplete[ControllerMessage]]
  val messageSink: Sink[ServerMessage, Future[Done]]
  val messageProcessingFlow: Flow[ControllerMessage, ServerMessage, NotUsed]
}

case class ControllerStreamConfigImpl(dataPathManageService: DataPathManageService, messageSendService: MessageSendService)
  extends ControllerStreamConfig {

  private implicit val ec: ExecutionContext = Executors.service
  private val messageProcessingService: MessageProcessingService = new MessageProcessingServiceImpl(
    new CaptivePortalServiceImpl(new CaptivePortalServiceConfigImpl())
  )

  val messageSource: Source[ControllerMessage, SourceQueueWithComplete[ControllerMessage]] = ControllerMessageSource()
  val messageSink: Sink[ServerMessage, Future[Done]] = ControllerMessageSink(messageSendService)
  val messageProcessingFlow: Flow[ControllerMessage, ServerMessage, NotUsed] = MessageProcessingFlow(messageProcessingService)
}
