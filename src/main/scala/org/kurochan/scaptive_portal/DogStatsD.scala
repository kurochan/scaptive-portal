package org.kurochan.scaptive_portal

import com.timgroup.statsd.{NoOpStatsDClient, NonBlockingStatsDClient, StatsDClient}

import scala.concurrent.{ExecutionContext, Future}

object DogStatsD {

  def client: StatsDClient = _client

  def enable: Unit = {
    _client = createClient(true)
  }

  def disable: Unit = {
    _client = createClient(false)
  }

  private var _client = createClient(false)

  private def createClient(enabled: Boolean): StatsDClient = {

    if (enabled) {
      new NonBlockingStatsDClient(
        "captive_portal",
        "127.0.0.1",
        8125
      )
    } else {
      new NoOpStatsDClient()
    }
  }
}

object DogStatsDUtil {

  def recordExecutionTime[T](sampleRate: Double, tags: String*)(f: => T): T = {
    val start = System.nanoTime()
    val result = f
    val end = System.nanoTime()
    val duration = (end - start) / 1000000.0
    DogStatsD.client.histogram("execution_time", duration, sampleRate, tags: _*)

    result
  }

  def asyncRecordExecutionTime[T](sampleRate: Double, tags: String*)(f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val start = System.nanoTime()
    val result = f

    result.onComplete { _ =>
      val end = System.nanoTime()
      val duration = (end - start) / 1000000.0
      DogStatsD.client.histogram("execution_time", duration, sampleRate, tags: _*)
    }

    result
  }
}
