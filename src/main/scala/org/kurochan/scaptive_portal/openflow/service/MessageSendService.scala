package org.kurochan.scaptive_portal.openflow.service

import com.typesafe.scalalogging.StrictLogging
import org.kurochan.scaptive_portal.controller.model.ServerMessage
import org.kurochan.scaptive_portal.openflow.util.NettyConverters._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait MessageSendService {

  def sendMessageToSwitch(message: ServerMessage): Future[Unit]
}

class MessageSendServiceImpl(dataPathManageService: DataPathManageService)(implicit ec: ExecutionContext)
  extends MessageSendService
  with StrictLogging {

  def sendMessageToSwitch(message: ServerMessage): Future[Unit] = {

    val maybeChannel = dataPathManageService.findChannelByDataPathId(message.dataPathId)

    val future = maybeChannel match {

      case Some(channel) if channel.isActive => {
        val result = Try {
          val buf = channel.alloc().buffer()
          message.ofMessage.writeTo(buf)
          channel.writeAndFlush(buf).asScala
        }
        result match {
          case Success(r) => r
          case Failure(e) =>
            logger.error("MessageSendService: failed to send message.", e)
            Future.successful(())
        }
      }

      case Some(_) => {
        logger.error("MessageSendService: channel is not active.")
        Future.successful(())
      }

      case None => {
        logger.warn(s"Channel of data path does not exist. id: ${message.dataPathId}")
        Future.successful(())
      }
    }

    future.map(_ => ())
  }
}
