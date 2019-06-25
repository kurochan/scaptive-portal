package org.kurochan.scaptive_portal.openflow.handler

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import org.kurochan.scaptive_portal.openflow.service.{DataPathManageService, MessageReceiveService}
import org.projectfloodlight.openflow.protocol.OFMessage

import scala.util.{Failure, Success, Try}

@Sharable
class ControllerServerHandler(dataPathManageService: DataPathManageService, messageService: MessageReceiveService)
  extends ChannelInboundHandlerAdapter
  with StrictLogging {

  override def channelRead(ctx: ChannelHandlerContext, message: Any): Unit = {

    dataPathManageService.updateChannel(ctx.channel.id.asShortText, ctx.channel)

    message match {
      case msg: OFMessage => {
        val sendMessages = messageService.handleMessage(ctx.channel.id.asShortText, msg)
        val result = Try {
          val buf = ctx.channel.alloc().buffer()
          sendMessages.foreach { sendMsg =>
            logger.debug(s"send message: ChannelId: ${ctx.channel.id.asShortText}, Message: ${sendMsg}")
            sendMsg.writeTo(buf)
          }
          ctx.channel.writeAndFlush(buf)
        }
        result match {
          case Success(r) => r
          case Failure(e) =>
            logger.error("ControllerServerHandler: failed to send message.", e)
        }
      }
      case _ => throw new IllegalStateException(s"message is NOT OFMessage: ${message.getClass}")
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    logger.error("unexpected error", cause)
    ctx.close()
  }
}
