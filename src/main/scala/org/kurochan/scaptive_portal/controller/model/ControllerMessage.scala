package org.kurochan.scaptive_portal.controller.model

import java.net.InetAddress

import org.projectfloodlight.openflow.protocol.OFMessage

sealed trait ControllerMessage {}

case class ServerMessage(dataPathId: Long, ofMessage: OFMessage) extends ControllerMessage

trait APIMessage extends ControllerMessage
case class APIRegisteredMessage(dataPathId: Long, userIP: InetAddress) extends APIMessage
