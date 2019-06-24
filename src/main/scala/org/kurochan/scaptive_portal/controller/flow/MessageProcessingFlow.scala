package org.kurochan.scaptive_portal.controller.flow

import akka.NotUsed
import akka.stream.scaladsl.Flow
import org.kurochan.scaptive_portal.controller.model.{ControllerMessage, ServerMessage}
import org.kurochan.scaptive_portal.controller.service.MessageProcessingService

object MessageProcessingFlow {

  def apply(messageProcessingService: MessageProcessingService): Flow[ControllerMessage, ServerMessage, NotUsed] = {

    Flow[ControllerMessage].mapAsync(2)(messageProcessingService.process).mapConcat(identity)
  }
}
