package org.kurochan.scaptive_portal.controller.flow

import akka.Done
import akka.stream.scaladsl.Sink
import org.kurochan.scaptive_portal.controller.model.ServerMessage
import org.kurochan.scaptive_portal.openflow.service.MessageSendService

import scala.concurrent.Future

object ControllerMessageSink {

  def apply(messageSendService: MessageSendService): Sink[ServerMessage, Future[Done]] = {

    Sink.foreachAsync[ServerMessage](2)(messageSendService.sendMessageToSwitch)
  }
}
