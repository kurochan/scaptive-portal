package org.kurochan.scaptive_portal.openflow.service

import java.util.concurrent.TimeUnit

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import io.netty.channel.Channel

trait DataPathManageServiceConfig {

  val channelCache: Cache[String, Channel]
  val channelIdToDataPathIdCache: Cache[String, java.lang.Long]
  val dataPathIdToChannelIdCache: Cache[java.lang.Long, String]
}

case class DataPathManageServiceConfigImpl() extends DataPathManageServiceConfig {

  val channelCache: Cache[String, Channel] =
    Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

  val channelIdToDataPathIdCache: Cache[String, java.lang.Long] =
    Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()

  val dataPathIdToChannelIdCache: Cache[java.lang.Long, String] =
    Caffeine.newBuilder().expireAfterAccess(1, TimeUnit.MINUTES).build()
}
