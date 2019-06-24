package org.kurochan.scaptive_portal.controller

import akka.Done
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.GraphDSL.Implicits._
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, SourceQueue}
import com.typesafe.scalalogging.StrictLogging
import org.kurochan.scaptive_portal.controller.model.ControllerMessage

import scala.concurrent.{ExecutionContext, Future}

trait ControllerStream {

  def stream()(
    implicit system: ActorSystem,
    mat: Materializer,
    ec: ExecutionContext,
    killSwitch: SharedKillSwitch
  ): (SourceQueue[ControllerMessage], Future[Done])
}

class ControllerStreamImpl(config: ControllerStreamConfig) extends ControllerStream with StrictLogging {

  private val decider: Supervision.Decider = {
    case e: Exception => {
      logger.error(s"ControllerStream: caught exception!! resume.", e)
      Supervision.Resume
    }
  }

  def stream()(
    implicit system: ActorSystem,
    mat: Materializer,
    ec: ExecutionContext,
    killSwitch: SharedKillSwitch
  ): (SourceQueue[ControllerMessage], Future[Done]) = {
    logger.info("OpenFlowControllerStream start")

    val source = config.messageSource
    val kill = killSwitch.flow[ControllerMessage]

    val flow = config.messageProcessingFlow

    val sink = config.messageSink

    val graph = RunnableGraph.fromGraph(GraphDSL.create(source, kill, flow, sink)((source, _, _, sink) => (source, sink)) {
      implicit b => (source, kill, flow, sink) =>
        source ~> kill ~> flow ~> sink

        ClosedShape
    })

    val (sourceQueue, sinkFuture) = graph.withAttributes(ActorAttributes.supervisionStrategy(decider)).run()
    val sourceFuture = sourceQueue.watchCompletion()

    val future = Future.sequence(Seq(sourceFuture, sinkFuture)).map(_.head)

    future.foreach { _ =>
      logger.info("OpenFlowControllerStream end")
    }

    (sourceQueue, future)
  }
}
