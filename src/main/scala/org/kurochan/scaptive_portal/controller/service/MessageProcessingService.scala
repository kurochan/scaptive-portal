package org.kurochan.scaptive_portal.controller.service

import org.kurochan.scaptive_portal.controller.model.{APIRegisteredMessage, ControllerMessage, ServerMessage}
import org.projectfloodlight.openflow.protocol.{OFFlowRemoved, OFPacketIn}

import scala.concurrent.{ExecutionContext, Future}

trait MessageProcessingService {

  def process(messageIn: ControllerMessage): Future[List[ServerMessage]]
}

class MessageProcessingServiceImpl(captivePortalService: CaptivePortalService)(implicit ec: ExecutionContext) extends MessageProcessingService {

  def process(messageIn: ControllerMessage): Future[List[ServerMessage]] = {

    messageIn match {

      case ServerMessage(dataPathId, message: OFPacketIn) => {
        captivePortalService.handlePacketIn(dataPathId, message)
      }
      case ServerMessage(dataPathId, message: OFFlowRemoved) => {
        captivePortalService.handleFlowRemoved(dataPathId, message)
      }
      case APIRegisteredMessage(dataPathId, ip) => {
        captivePortalService.handleUserRegistered(dataPathId, ip)
      }
      case _ => Future.successful(Nil)
    }
  }
}
