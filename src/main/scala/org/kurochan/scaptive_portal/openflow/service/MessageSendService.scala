package org.kurochan.scaptive_portal.openflow.service

import com.typesafe.scalalogging.StrictLogging
import org.kurochan.scaptive_portal.controller.model.ServerMessage
import org.kurochan.scaptive_portal.openflow.util.NettyConverters._

import scala.concurrent.{ExecutionContext, Future}

trait MessageSendService {

  def sendMessageToSwitch(message: ServerMessage): Future[Unit]
}

class MessageSendServiceImpl(dataPathManageService: DataPathManageService)(implicit ec: ExecutionContext)
  extends MessageSendService
  with StrictLogging {

  def sendMessageToSwitch(message: ServerMessage): Future[Unit] = {

    val maybeChannel = dataPathManageService.findChannelByDataPathId(message.dataPathId)

    val future = maybeChannel match {

      case Some(channel) => {
        val buf = channel.alloc().buffer()
        message.ofMessage.writeTo(buf)
        channel.writeAndFlush(buf).asScala
      }

      case None => {
        logger.warn(s"Channel of data path does not exist. id: ${message.dataPathId}")
        Future.successful(())
      }
    }

    future.map(_ => ())
  }
}
