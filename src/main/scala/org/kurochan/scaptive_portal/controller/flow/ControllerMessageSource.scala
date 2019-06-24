package org.kurochan.scaptive_portal.controller.flow

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import org.kurochan.scaptive_portal.controller.model.ControllerMessage

object ControllerMessageSource {

  def apply(): Source[ControllerMessage, SourceQueueWithComplete[ControllerMessage]] = {

    Source.queue[ControllerMessage](10000, OverflowStrategy.dropNew)
  }
}
