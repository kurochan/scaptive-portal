package org.kurochan.scaptive_portal.shared.repository

import com.redis.RedisClientPool
import com.redis.serialization.Parse.Implicits._
import com.typesafe.config.{Config, ConfigFactory}

trait RedisRepository {
  val clientPool: RedisClientPool
  def getInt(key: String): Option[Int]
  def setInt(key: String, value: Int, ttl: Option[Int] = None): Int
  def getString(key: String): Option[String]
  def setString(key: String, value: String, ttl: Option[Int] = None): String
}

class RedisRepositoryImpl(val clientPool: RedisClientPool) extends RedisRepository {

  def getInt(key: String): Option[Int] = clientPool.withClient { c =>
    c.get[Int](key)
  }

  def setInt(key: String, value: Int, ttl: Option[Int] = None): Int = {
    clientPool.withClient { c =>
      c.set(key, value)
    }
    ttl match {
      case Some(t) => clientPool.withClient { c =>
        c.expire(key, t)
      }
      case None =>
    }
    value
  }

  def getString(key: String): Option[String] = clientPool.withClient { c =>
    c.get[String](key)
  }

  def setString(key: String, value: String, ttl: Option[Int] = None): String = {
    clientPool.withClient { c =>
      c.set(key, value)
    }
    ttl match {
      case Some(t) =>
        clientPool.withClient { c =>
          c.expire(key, t)
        }
      case None =>
    }
    value
  }
}

object RedisRepositoryImpl {
  private val globalConfig: Config = ConfigFactory.load()
  val repository = new RedisRepositoryImpl(
    new RedisClientPool(
      globalConfig.getString("captive-portal.redis.host"),
      globalConfig.getInt("captive-portal.redis.port")
    )
  )
}
