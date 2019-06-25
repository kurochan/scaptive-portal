package org.kurochan.scaptive_portal.openflow.service

import akka.stream.QueueOfferResult.{Dropped, Enqueued, Failure, QueueClosed}
import akka.stream.scaladsl.SourceQueue
import com.typesafe.scalalogging.LazyLogging
import org.kurochan.scaptive_portal.controller.model.{ControllerMessage, ServerMessage}
import org.kurochan.scaptive_portal.openflow.model.FlowPriority
import org.projectfloodlight.openflow.protocol._
import org.projectfloodlight.openflow.protocol.`match`.MatchField
import org.projectfloodlight.openflow.protocol.action.OFAction
import org.projectfloodlight.openflow.types.{EthType, OFPort}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration.Duration

trait MessageReceiveService {
  def handleMessage(channelId: String, message: OFMessage): List[OFMessage]
}

class MessageReceiveServiceImpl(dataPathManageService: DataPathManageService, messageQueue: SourceQueue[ControllerMessage])
  extends MessageReceiveService
  with LazyLogging {

  private val ofFactory = OFFactories.getFactory(OFVersion.OF_13)

  def handleMessage(channelId: String, message: OFMessage): List[OFMessage] = {

    val sendMessages: ListBuffer[OFMessage] = new ListBuffer()
    val maybeDataPathId = dataPathManageService.findDataPathIdByChannelId(channelId)

    (message, maybeDataPathId) match {
      case (msg: OFHello, None) => {
        logger.info(s"Hello: channelId: ${channelId}, OFHello: ${msg}")
        if (msg.getVersion.getWireVersion < OFVersion.OF_13.wireVersion) {
          logger.error(s"channelId: ${channelId}, got OFHello but unsupported wire version: ${msg.getVersion.getWireVersion}, ${msg}")
          throw new IllegalStateException(
            s"channelId: ${channelId}, got OFHello but unsupported wire version: ${msg.getVersion.getWireVersion}, ${msg}"
          )
        }

        // Hello
        sendMessages += ofFactory.buildHello().setXid(msg.getXid).build()

        // FeatureRequest
        sendMessages += ofFactory.buildFeaturesRequest().setXid(msg.getXid).build()

        // Allow ARP
        val arpMatch = ofFactory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.ARP).build()
        val arpAction = ofFactory.actions().buildOutput().setPort(OFPort.NORMAL).setMaxLen(Integer.MAX_VALUE).build()
        val arpActions: List[OFAction] = List(arpAction)
        sendMessages += ofFactory.buildFlowAdd().setMatch(arpMatch).setActions(arpActions.asJava).setPriority(FlowPriority.DEFAULT_PACKET_ARP).build()

        // Default Controller (IPv4 Only)
        val ipv4Match = ofFactory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).build()
        val defaultAction = ofFactory.actions().buildOutput().setPort(OFPort.CONTROLLER).setMaxLen(Integer.MAX_VALUE).build()
        val defaultActions: List[OFAction] = List(defaultAction)
        sendMessages += ofFactory.buildFlowAdd().setMatch(ipv4Match).setActions(defaultActions.asJava).setPriority(FlowPriority.DEFAULT_PACKET_IN).build()
      }

      case (msg: OFFeaturesReply, _) => {
        val dataPathId = msg.getDatapathId.getLong
        logger.debug(s"channelId: ${channelId}, dataPathId: ${dataPathId}, OFFeaturesReply: ${msg}")
        dataPathManageService.updateDataPath(dataPathId, channelId)
      }

      case (msg: OFPacketIn, Some(dataPathId)) => {
        logger.debug(s"dataPathId: ${dataPathId}, OFPacketIn: ${msg}")

        val status = Await.result(messageQueue.offer(ServerMessage(dataPathId, msg)), Duration.Inf)

        status match {
          case Enqueued =>
          case Dropped => logger.warn(s"controller queue is full!! message dropped.: ${msg.getMatch}")
          case QueueClosed => logger.error("controller queue is closed!!")
          case Failure(e) => logger.error("controller queue error.", e)
        }
      }

      case (msg: OFFlowRemoved, Some(dataPathId)) => {
        logger.debug(s"dataPathId: ${dataPathId}, OFFlowRemoved: ${msg}")
      }

      case (msg: OFEchoRequest, dataPathId) => {
        logger.debug(s"dataPathId: ${dataPathId}, channelId: ${channelId}, OFEchoRequest: ${msg}")

        // EchoRply
        sendMessages += ofFactory.buildEchoReply().setData(msg.getData).setXid(msg.getXid).build()
      }

      case (msg: OFEchoReply, Some(dataPathId)) => {
        logger.debug(s"dataPathId: ${dataPathId}, OFEchoReply: ${msg}")
      }

      case (msg: OFMessage, Some(dataPathId)) => {
        logger.warn(s"dataPathId: ${dataPathId}, unsupported message type: ${msg}")
      }

      case (msg: OFMessage, None) => {
        logger.error(s"channelId: ${channelId}, illegalstate!! message: ${msg}")
        throw new IllegalStateException(s"channelId: ${channelId}, illegalstate!! message: ${msg}")
      }
    }

    sendMessages.toList
  }
}
