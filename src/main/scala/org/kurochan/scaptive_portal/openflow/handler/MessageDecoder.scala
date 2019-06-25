package org.kurochan.scaptive_portal.openflow.handler

import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import org.projectfloodlight.openflow.exceptions.OFParseError
import org.projectfloodlight.openflow.protocol.{OFFactories, OFMessage}

import scala.util.{Failure, Success, Try}

class MessageDecoder() extends ByteToMessageDecoder with StrictLogging {

  private val reader = OFFactories.getGenericReader

  override def decode(ctx: ChannelHandlerContext, in: ByteBuf, out: java.util.List[Object]): Unit = {

    // see https://github.com/floodlight/floodlight/blob/163d6b157463d62639b999320ee28037f3fa61d8/src/main/java/net/floodlightcontroller/core/internal/OFMessageDecoder.java#L63
    if (!ctx.channel().isActive) {
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
          skipBrokenBytesAndDecode(in)
        }
      }
    }.takeWhile(_.nonEmpty)

    messages.foreach {
      case Some(msg) => out.add(msg)
      case None =>
    }
  }

  private def skipBrokenBytesAndDecode(in: ByteBuf): Option[OFMessage] = {
    val indexBeforeSkip = in.readerIndex()
    var continue = true
    var lastMsg: Option[OFMessage] = None

    while (continue && in.isReadable) {
      val ofMessageTry = Try(Option(reader.readFrom(in)))

      ofMessageTry match {
        case Success(ofMessage) => {
          continue = false
          lastMsg = ofMessage
        }
        case Failure(_: OFParseError) => {
          in.readByte()
        }
        case Failure(_: IllegalArgumentException) => {
          in.readByte()
        }
        case Failure(e) => {
          logger.error(s"unknown error", e)
          in.readByte()
        }
      }
    }

    logger.warn(
      s"MessageDecoder: ${in.readerIndex() - indexBeforeSkip} bytes discarded. isReadable: ${in.isReadable} (${in.readableBytes()} bytes), last message: ${lastMsg}"
    )

    lastMsg
  }
}
