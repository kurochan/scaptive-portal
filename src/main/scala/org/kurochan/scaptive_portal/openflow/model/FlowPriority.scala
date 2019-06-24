package org.kurochan.scaptive_portal.openflow.model

object FlowPriority {

  val UNAUTHORIZED_USER_DNS = 2210
  val UNAUTHORIZED_USER_DNS_CONTROLLER = 2200

  val AUTHORIZED_USER_IN = 2110
  val AUTHORIZED_USER_OUT = 2100

  val UNAUTHORIZED_USER_DROP_UDP = 2010
  val UNAUTHORIZED_USER_DROP_OTHER = 2000

  val DEFAULT_PACKET_ARP = 1100
  val DEFAULT_PACKET_IN = 1000
}
