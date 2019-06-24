package org.kurochan.scaptive_portal.openflow.util

import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}

import scala.concurrent.{Future, Promise}

object NettyConverters {

  implicit class ChannelFutureToFuture(channelFuture: ChannelFuture) {

    def asScala: Future[Channel] = {
      val p = Promise[Channel]

      channelFuture.addListener(new ChannelFutureListener {
        override def operationComplete(future: ChannelFuture): Unit = {
          if (future.isSuccess() && future.isDone()) {
            p.trySuccess(future.channel)
          } else {
            p.tryFailure(future.cause())
          }
        }
      })

      p.future
    }
  }
}
