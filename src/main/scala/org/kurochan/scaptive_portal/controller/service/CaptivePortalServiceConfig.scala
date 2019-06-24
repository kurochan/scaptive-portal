package org.kurochan.scaptive_portal.controller.service

import org.kurochan.scaptive_portal.shared.repository.{RedisRepository, RedisRepositoryImpl}

trait CaptivePortalServiceConfig {
  val redisRepository: RedisRepository
}

class CaptivePortalServiceConfigImpl extends CaptivePortalServiceConfig {
  val redisRepository: RedisRepository = RedisRepositoryImpl.repository
}
