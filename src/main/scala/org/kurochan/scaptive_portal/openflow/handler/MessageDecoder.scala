package org.kurochan.scaptive_portal.openflow.handler

import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.projectfloodlight.openflow.protocol.OFFactories

import scala.util.{Failure, Success, Try}

class MessageDecoder() extends ByteToMessageDecoder with StrictLogging {

  private val reader = OFFactories.getGenericReader

  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: java.util.List[Object]): Unit = {

    // see https://github.com/floodlight/floodlight/blob/163d6b157463d62639b999320ee28037f3fa61d8/src/main/java/net/floodlightcontroller/core/internal/OFMessageDecoder.java#L63
    if(!ctx.channel().isActive) {
      return
    }

    val messages = Iterator.continually {
      val ofMessageTry = Try(Option(reader.readFrom(in)))

      ofMessageTry match {
        case Success(ofMessage) => {
          ofMessage
        }
        case Failure(e) => {
          logger.error(s"MessageDecoder ERROR!: ${e.getMessage}")
          in.readByte()
          None
        }
      }
    }.takeWhile(_.nonEmpty)

    messages.foreach {
      case Some(msg) => out.add(msg)
      case None =>
    }
  }
}
