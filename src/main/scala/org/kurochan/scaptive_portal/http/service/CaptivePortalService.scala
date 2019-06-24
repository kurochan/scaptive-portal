package org.kurochan.scaptive_portal.http.service

import java.net.InetAddress
import java.util.UUID

import akka.stream.QueueOfferResult.{Dropped, Enqueued, Failure, QueueClosed}
import com.typesafe.scalalogging.LazyLogging
import org.kurochan.scaptive_portal.controller.model.APIRegisteredMessage

import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class UserStatus(
  authorized: Boolean,
  uid: String
)

trait CaptivePortalService {

  def getOrCreateUserStatus(maybeUid: Option[String], maybeIp: Option[InetAddress]): UserStatus
  def registerUser(uid: String, maybeIp: Option[InetAddress]): UserStatus
}

class CaptivePortalServiceImpl(captivePortalServiceConfig: CaptivePortalServiceConfig) extends CaptivePortalService with LazyLogging {

  def getOrCreateUserStatus(maybeUid: Option[String], maybeIp: Option[InetAddress]): UserStatus = {
    val uid = maybeUid.getOrElse(generateUid)

    val ip = maybeIp.map(_.getHostAddress).getOrElse("")
    val authorized = captivePortalServiceConfig.redisRepository.getInt(ip).nonEmpty

    UserStatus(authorized, uid)
  }

  def registerUser(uid: String, maybeIp: Option[InetAddress]): UserStatus = {

    val ip = maybeIp.map(_.getHostAddress).getOrElse("")
    captivePortalServiceConfig.redisRepository.setInt(ip, 1, Some(432000))

    for {
      ip <- maybeIp.toList
      dataPathId <- captivePortalServiceConfig.dataPathManageService.findAllDataPathId()
    } yield {
      val status = Await.result(captivePortalServiceConfig.messageQueue.offer(APIRegisteredMessage(dataPathId, ip)), Duration.Inf)
      status match {
        case Enqueued =>
        case Dropped => logger.warn(s"controller queue is full!! message dropped.: registered")
        case QueueClosed => logger.error("controller queue is closed!!")
        case Failure(e) => logger.error("controller queue error.", e)
      }
    }

    UserStatus(true, uid)
  }

  private def generateUid: String = {
    UUID.randomUUID().toString
  }
}
