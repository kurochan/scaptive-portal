package org.kurochan.scaptive_portal.server

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelInitializer, ChannelOption}
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import io.netty.util.ResourceLeakDetector
import org.kurochan.scaptive_portal.openflow.util.NettyConverters._

import scala.concurrent.Future

trait OpenFlowControllerServer {

  def start(): Future[Unit]
  def shutdown(): Future[Unit]
}

class OpenFlowControllerServerImpl(config: OpenFlowControllerServerConfig) extends OpenFlowControllerServer {

  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED)

  private implicit val ec = config.executionContext

  private var maybeChannel: Option[Channel] = None
  private var maybeClosedListener: Option[Future[Unit]] = None
  private var maybeEventGroup: Option[NioEventLoopGroup] = None
  private var maybeWorkerGroup: Option[NioEventLoopGroup] = None

  def start(): Future[Unit] = {

    val eventGroup = new NioEventLoopGroup(2, ec)
    maybeEventGroup = Some(eventGroup)
    val workerGroup = new NioEventLoopGroup(2, ec)
    maybeWorkerGroup = Some(workerGroup)
    val bootstrap = new ServerBootstrap()

    bootstrap
      .group(eventGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .option[Integer](ChannelOption.SO_BACKLOG, 200)
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler(new ChannelInitializer[SocketChannel] {

        override def initChannel(ch: SocketChannel): Unit = {
          val p = ch.pipeline()
          p.addLast(config.messageDecoder)
          p.addLast(config.controllerServerHandler)
        }

      })

    val bind = bootstrap.bind(config.port)
    maybeChannel = Some(bind.channel)
    val close = bind.asScala.flatMap(_.closeFuture.asScala).map(_ => ())
    maybeClosedListener = Some(close)

    close
  }

  def shutdown(): Future[Unit] = {

    maybeChannel.foreach(_.close())
    maybeClosedListener match {
      case Some(future) =>
        future.map { _ =>
          maybeEventGroup.foreach(_.shutdownGracefully().sync())
          maybeWorkerGroup.foreach(_.shutdownGracefully().sync())
          ()
        }
      case None => Future.successful(())
    }
  }
}
