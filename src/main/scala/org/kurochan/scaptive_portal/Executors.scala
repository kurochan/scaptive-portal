package org.kurochan.scaptive_portal

import akka.actor.ActorSystem

import scala.concurrent.ExecutionContextExecutor

object Executors {

  System.setProperty("scala.concurrent.context.numThreads", "2")
  System.setProperty("scala.concurrent.context.maxThreads", "2")

  @volatile private[scaptive_portal] var currentActorSystem: ActorSystem = _
  private def maybeActorSystem: Option[ActorSystem] = Option(currentActorSystem)
  private def current = maybeActorSystem.getOrElse(sys.error("set actor system before using it."))

  lazy val service: ExecutionContextExecutor = current.dispatchers.lookup("service-dispatcher")
  lazy val repository: ExecutionContextExecutor = current.dispatchers.lookup("io-dispatcher")
}
