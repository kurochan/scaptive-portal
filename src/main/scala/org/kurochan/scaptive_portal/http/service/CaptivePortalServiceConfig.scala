package org.kurochan.scaptive_portal.http.service

import akka.stream.scaladsl.SourceQueue
import org.kurochan.scaptive_portal.controller.model.ControllerMessage
import org.kurochan.scaptive_portal.openflow.service.DataPathManageService
import org.kurochan.scaptive_portal.shared.repository.{RedisRepository, RedisRepositoryImpl}

trait CaptivePortalServiceConfig {
  val redisRepository: RedisRepository
  val dataPathManageService: DataPathManageService
  val messageQueue: SourceQueue[ControllerMessage]
}

class CaptivePortalServiceConfigImpl(
  val dataPathManageService: DataPathManageService,
  val messageQueue: SourceQueue[ControllerMessage]
  ) extends CaptivePortalServiceConfig {
  val redisRepository: RedisRepository = RedisRepositoryImpl.repository
}
