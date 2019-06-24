package org.kurochan.scaptive_portal.controller.service

import java.net.{Inet4Address, InetAddress}

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import net.floodlightcontroller.packet._
import org.kurochan.scaptive_portal.controller.model.ServerMessage
import org.kurochan.scaptive_portal.openflow.model.{FlowPriority, FlowTTL}
import org.projectfloodlight.openflow.protocol._
import org.projectfloodlight.openflow.protocol.`match`.MatchField
import org.projectfloodlight.openflow.protocol.action.OFAction
import org.projectfloodlight.openflow.types._

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

trait CaptivePortalService {

  def handlePacketIn(dataPathId: Long, message: OFPacketIn): Future[List[ServerMessage]]

  def handleFlowRemoved(dataPathId: Long, message: OFFlowRemoved): Future[List[ServerMessage]]

  def handleUserRegistered(dataPathId: Long, ip: InetAddress): Future[List[ServerMessage]]
}

class CaptivePortalServiceImpl(captivePortalServiceConfig: CaptivePortalServiceConfig)(implicit ec: ExecutionContext)
  extends CaptivePortalService
  with LazyLogging {

  private val globalConfig: Config = ConfigFactory.load()

  private val WEB_IP_ADDR = IPv4Address.of(globalConfig.getString("captive-portal.ip.web"))
  private val FAKE_DNS_IP_ADDR = IPv4Address.of(globalConfig.getString("captive-portal.ip.fake-dns"))
  private val WHITELISTED_IP_ADDRS = globalConfig.getStringList("captive-portal.ip.whitelist").asScala.map(r => IPv4AddressWithMask.of(r)).toSet
  private val WHITELISTED_IPS = Set(
    WEB_IP_ADDR.withMaskOfLength(32),
    FAKE_DNS_IP_ADDR.withMaskOfLength(32),
  ) ++ WHITELISTED_IP_ADDRS

  private val ofFactory = OFFactories.getFactory(OFVersion.OF_13)

  def handlePacketIn(dataPathId: Long, message: OFPacketIn): Future[List[ServerMessage]] = Future {

    val ether = new Ethernet().deserialize(message.getData, 0, message.getData.length).asInstanceOf[Ethernet]
    (ether.getPayload, ether.getPayload.getPayload) match {
      case (ipv4: IPv4, _) if isAllowedIP(ipv4.getSourceAddress) => {
        handleIPv4AuthorizedPacketIn(dataPathId, ipv4.getSourceAddress, message)
      }
      case (ipv4: IPv4, _) if isAllowedIP(ipv4.getDestinationAddress) => {
        handleIPv4AuthorizedPacketIn(dataPathId, ipv4.getDestinationAddress, message)
      }
      case (ipv4: IPv4, _) if isWhitelistedIP(ipv4.getSourceAddress) => {
        handleIPv4AuthorizedPacketIn(dataPathId, ipv4.getSourceAddress, message)
      }
      case (ipv4: IPv4, _) if isWhitelistedIP(ipv4.getDestinationAddress) => {
        handleIPv4AuthorizedPacketIn(dataPathId, ipv4.getDestinationAddress, message)
      }
      case (ipv4: IPv4, udp: UDP) => {
        logger.debug(s"IPv4 UDP Packet In: ${ether}")
        handleIPv4UDPUnauthorizedPacketIn(dataPathId, ipv4, udp, message)
      }
      case (ipv4: IPv4, _) => {
        logger.debug(s"IPv4 Packet In: ${ether}")
        handleIPv4DefaultUnauthorizedPacketIn(dataPathId, ipv4, message)
      }
      case (ipv6: IPv6, _) => {
        logger.debug(s"IPv6 Packet In: ${ether}")
        Nil
      }
      case _ => {
        logger.debug(s"Default Packet In: ${ether}")
        Nil
      }
    }
  }

  def handleFlowRemoved(dataPathId: Long, message: OFFlowRemoved): Future[List[ServerMessage]] = Future {
    println("###########")
    println(message)
    Nil
  }

  def handleUserRegistered(dataPathId: Long, ip: InetAddress): Future[List[ServerMessage]] = Future {
    val maybeIPv4 = ip match {
      case ipv4: Inet4Address => Some(IPv4Address.of(ipv4))
      case _ => None
    }

    val outMessages = ListBuffer[ServerMessage]()

    // cleanup unauthorized rules
    maybeIPv4.foreach { ipv4 =>
      val srcMatch = ofFactory
        .buildMatch()
        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_SRC, ipv4)
        .build()
      val cleanupSrcFlowRem = ofFactory.buildFlowDelete().setMatch(srcMatch).build()
      outMessages.append(ServerMessage(dataPathId, cleanupSrcFlowRem))

      val dstMatch = ofFactory
        .buildMatch()
        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_DST, ipv4)
        .build()
      val cleanupDstFlowRem = ofFactory.buildFlowDelete().setMatch(dstMatch).build()
      outMessages.append(ServerMessage(dataPathId, cleanupDstFlowRem))
    }

    outMessages.toList
  }

  private def isWhitelistedIP[A <: IPAddress[A]](ipv4: IPv4Address): Boolean = {
    WHITELISTED_IPS.exists(_.contains(ipv4))
  }

  private def isAllowedIP[A <: IPAddress[A]](ipv4: IPv4Address): Boolean = {
    val addr = ipv4.toInetAddress.getHostAddress
    captivePortalServiceConfig.redisRepository.getInt(addr).nonEmpty
  }

  private def handleIPv4AuthorizedPacketIn(dataPathId: Long, ipv4: IPv4Address, message: OFPacketIn): List[ServerMessage] = {

    val outMessages = ListBuffer[ServerMessage]()

    val action: OFAction = ofFactory.actions().buildOutput().setPort(OFPort.NORMAL).build()

    val srcMatch = ofFactory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_SRC, ipv4).build()

    val srcFlowAdd = ofFactory
      .buildFlowAdd()
      .setMatch(srcMatch)
      .setActions(List(action).asJava)
      .setPriority(FlowPriority.AUTHORIZED_USER_OUT)
      .setHardTimeout(FlowTTL.AUTHORIZED_OUT_HARD)
      .setIdleTimeout(FlowTTL.AUTHORIZED_OUT_IDLE)
      // .setFlags(Set(OFFlowModFlags.SEND_FLOW_REM).asJava)
      .build()
    outMessages.append(ServerMessage(dataPathId, srcFlowAdd))

    val dstMatch = ofFactory.buildMatch().setExact(MatchField.ETH_TYPE, EthType.IPv4).setExact(MatchField.IPV4_DST, ipv4).build()
    val dstFlowAdd = ofFactory
      .buildFlowAdd()
      .setMatch(dstMatch)
      .setActions(List(action).asJava)
      .setPriority(FlowPriority.AUTHORIZED_USER_IN)
      .setHardTimeout(FlowTTL.AUTHORIZED_OUT_HARD)
      .setIdleTimeout(FlowTTL.AUTHORIZED_OUT_IDLE)
      // .setFlags(Set(OFFlowModFlags.SEND_FLOW_REM).asJava)
      .build()
    outMessages.append(ServerMessage(dataPathId, dstFlowAdd))

    val packetOut = ofFactory.buildPacketOut().setActions(List(action).asJava).setData(message.getData).setBufferId(OFBufferId.NO_BUFFER).build()
    outMessages.append(ServerMessage(dataPathId, packetOut))

    outMessages.toList
  }

  private def handleIPv4DefaultUnauthorizedPacketIn(dataPathId: Long, ipv4: IPv4, message: OFPacketIn): List[ServerMessage] = {

    val outMessages = ListBuffer[ServerMessage]()

    // drop tcp
    val dropTCPMatch = ofFactory
      .buildMatch()
      .setExact(MatchField.ETH_TYPE, EthType.IPv4)
      .setExact(MatchField.IPV4_SRC, ipv4.getSourceAddress)
      .setExact(MatchField.IPV4_DST, ipv4.getDestinationAddress)
      .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
      .build()
    val dropTCPFlowAdd = ofFactory
      .buildFlowAdd()
      .setMatch(dropTCPMatch)
      .setPriority(FlowPriority.UNAUTHORIZED_USER_DROP_OTHER)
      .setHardTimeout(FlowTTL.UNAUTHORIZED_DROP_HARD)
      .setIdleTimeout(FlowTTL.UNAUTHORIZED_DROP_IDLE)
      .setBufferId(OFBufferId.NO_BUFFER)
      .build()
    outMessages.append(ServerMessage(dataPathId, dropTCPFlowAdd))

    // drop icmp
    val dropICMPMatch = ofFactory
      .buildMatch()
      .setExact(MatchField.ETH_TYPE, EthType.IPv4)
      .setExact(MatchField.IPV4_SRC, ipv4.getSourceAddress)
      .setExact(MatchField.IPV4_DST, ipv4.getDestinationAddress)
      .setExact(MatchField.IP_PROTO, IpProtocol.ICMP)
      .build()
    val dropICMPFlowAdd = ofFactory
      .buildFlowAdd()
      .setMatch(dropICMPMatch)
      .setPriority(FlowPriority.UNAUTHORIZED_USER_DROP_OTHER)
      .setHardTimeout(FlowTTL.UNAUTHORIZED_DROP_HARD)
      .setIdleTimeout(FlowTTL.UNAUTHORIZED_DROP_IDLE)
      .setBufferId(OFBufferId.NO_BUFFER)
      .build()
    outMessages.append(ServerMessage(dataPathId, dropICMPFlowAdd))

    outMessages.toList
  }

  private def handleIPv4UDPUnauthorizedPacketIn(dataPathId: Long, ipv4: IPv4, udp: UDP, message: OFPacketIn): List[ServerMessage] = {

    val outMessages = ListBuffer[ServerMessage]()

    val normalOutAction: OFAction = ofFactory.actions().buildOutput().setPort(OFPort.NORMAL).build()

    // handle packet
    if (udp.getDestinationPort == TransportPort.of(53)) {

      // dns source NAT
      val dnsSrcMatch = ofFactory
        .buildMatch()
        .setExact(MatchField.ETH_TYPE, EthType.IPv4)
        .setExact(MatchField.IPV4_SRC, FAKE_DNS_IP_ADDR)
        .setExact(MatchField.IPV4_DST, ipv4.getSourceAddress)
        .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
        .setExact(MatchField.UDP_SRC, TransportPort.of(53))
        .setExact(MatchField.UDP_DST, udp.getSourcePort)
        .build()
      val dnsModifySrcAction: OFAction =
        ofFactory.actions().buildSetField().setField(ofFactory.oxms().buildIpv4Src().setValue(ipv4.getDestinationAddress).build()).build()
      val dnsSrcFlowAdd = ofFactory
        .buildFlowAdd()
        .setMatch(dnsSrcMatch)
        .setActions(List(dnsModifySrcAction, normalOutAction).asJava)
        .setPriority(FlowPriority.UNAUTHORIZED_USER_DNS)
        .setHardTimeout(FlowTTL.UNAUTHORIZED_OUT_HARD)
        .setIdleTimeout(FlowTTL.UNAUTHORIZED_OUT_IDLE)
        .setCookie(U64.of(123))
        .build()
      outMessages.append(ServerMessage(dataPathId, dnsSrcFlowAdd))

      // dns destination NAT
      val dnsModifyDstAction: OFAction =
        ofFactory.actions().buildSetField().setField(ofFactory.oxms().buildIpv4Dst().setValue(FAKE_DNS_IP_ADDR).build()).build()
      val packetOut = ofFactory
        .buildPacketOut()
        .setActions(List(dnsModifyDstAction, normalOutAction).asJava)
        .setData(message.getData)
        .setBufferId(OFBufferId.NO_BUFFER)
        .build()
      outMessages.append(ServerMessage(dataPathId, packetOut))
    }

    val dnsMatch = ofFactory
      .buildMatch()
      .setExact(MatchField.ETH_TYPE, EthType.IPv4)
      .setExact(MatchField.IPV4_SRC, ipv4.getSourceAddress)
      .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
      .setExact(MatchField.UDP_DST, TransportPort.of(53))
      .build()

    // cleanup old dns controller flow
    val cleanupDNSFlowRem = ofFactory.buildFlowDelete().setMatch(dnsMatch).build()
    outMessages.append(ServerMessage(dataPathId, cleanupDNSFlowRem))

    // default dns action
    val dnsControllerAction: OFAction = ofFactory.actions().buildOutput().setPort(OFPort.CONTROLLER).setMaxLen(-1).build()
    val dnsControllerFlowAdd = ofFactory
      .buildFlowAdd()
      .setMatch(dnsMatch)
      .setActions(List(dnsControllerAction).asJava)
      .setPriority(FlowPriority.UNAUTHORIZED_USER_DNS_CONTROLLER)
      .setHardTimeout(FlowTTL.UNAUTHORIZED_OUT_HARD)
      .setIdleTimeout(FlowTTL.UNAUTHORIZED_OUT_IDLE)
      .setBufferId(OFBufferId.NO_BUFFER)
      .build()
    outMessages.append(ServerMessage(dataPathId, dnsControllerFlowAdd))

    // drop non dns udp packets
    val dropMatch = ofFactory
      .buildMatch()
      .setExact(MatchField.ETH_TYPE, EthType.IPv4)
      .setExact(MatchField.IPV4_SRC, ipv4.getSourceAddress)
      .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
      .build()
    val dropFlowAdd = ofFactory
      .buildFlowAdd()
      .setMatch(dropMatch)
      .setPriority(FlowPriority.UNAUTHORIZED_USER_DROP_UDP)
      .setHardTimeout(FlowTTL.UNAUTHORIZED_DROP_HARD)
      .setIdleTimeout(FlowTTL.UNAUTHORIZED_DROP_IDLE)
      .setBufferId(OFBufferId.NO_BUFFER)
      .build()
    outMessages.append(ServerMessage(dataPathId, dropFlowAdd))

    outMessages.toList
  }
}
