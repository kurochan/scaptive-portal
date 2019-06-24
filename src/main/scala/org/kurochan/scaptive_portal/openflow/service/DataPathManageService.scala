package org.kurochan.scaptive_portal.openflow.service

import com.typesafe.scalalogging.StrictLogging
import io.netty.channel.Channel

import scala.collection.JavaConverters._

trait DataPathManageService {

  def findAllDataPathId(): List[Long]

  def findDataPathIdByChannelId(channelId: String): Option[Long]

  def findChannelByDataPathId(dataPathId: Long): Option[Channel]

  def updateChannel(channelId: String, channel: Channel)

  def updateDataPath(dataPathId: Long, newChannelId: String)
}

class DataPathManageServiceImpl(config: DataPathManageServiceConfig) extends DataPathManageService with StrictLogging {

  def findAllDataPathId(): List[Long] = {
    config.dataPathIdToChannelIdCache.asMap().keySet().asScala.toList.map(_.toLong)
  }

  def findDataPathIdByChannelId(channelId: String): Option[Long] = {
    logger.debug(s"find data path by channel id: ${channelId}")
    val dataPathId = config.channelIdToDataPathIdCache.getIfPresent(channelId)

    // specify java.lang.Long to prevent auto boxing from null to 0
    Option(dataPathId).map { id: java.lang.Long =>
      // touch cache
      config.dataPathIdToChannelIdCache.getIfPresent(dataPathId)
      config.channelCache.getIfPresent(channelId)
      id.toLong
    }
  }

  def findChannelByDataPathId(dataPathId: Long): Option[Channel] = {
    logger.debug(s"find channel by data path id: ${dataPathId}")
    val channelId = Option(config.dataPathIdToChannelIdCache.getIfPresent(dataPathId))

    // touch cache
    config.channelIdToDataPathIdCache.getIfPresent(channelId)

    channelId.flatMap(id => Option(config.channelCache.getIfPresent(id)))
  }

  def updateChannel(channelId: String, channel: Channel): Unit = {
    logger.debug(s"update channel: channel: ${channelId}")
    config.channelCache.put(channelId, channel)
  }

  def updateDataPath(dataPathId: Long, newChannelId: String): Unit = {
    logger.info(s"update data path: data path: ${dataPathId}, channel: ${newChannelId}")

    val oldChannelId = Option(config.dataPathIdToChannelIdCache.getIfPresent(dataPathId))

    oldChannelId.foreach(id => revokeOldChannel(dataPathId, id))

    // touch cache
    config.channelCache.getIfPresent(newChannelId)

    config.channelIdToDataPathIdCache.put(newChannelId, dataPathId)
    config.dataPathIdToChannelIdCache.put(dataPathId, newChannelId)
  }

  private def revokeOldChannel(dataPathId: Long, oldChannelId: String): Unit = {
    logger.info(s"revoke old channel: data path: ${dataPathId}, old channel: ${oldChannelId}")

    val hasOldChannelCache = Option(config.channelCache.getIfPresent(oldChannelId)).nonEmpty
    if (hasOldChannelCache) {
      config.channelCache.invalidate(oldChannelId)
    }

    config.channelIdToDataPathIdCache.invalidate(oldChannelId)
  }
}
