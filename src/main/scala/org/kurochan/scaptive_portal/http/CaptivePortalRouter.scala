package org.kurochan.scaptive_portal.http

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpCookie, HttpCookiePair}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import org.kurochan.scaptive_portal.http.service.{CaptivePortalService, UserStatus}
import spray.json._

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}

trait CaptivePortalRouter {
  val route: Route
}

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val userStatusFormat = jsonFormat2(UserStatus)
}

class CaptivePortalRouterImpl(captivePortalService: CaptivePortalService) extends CaptivePortalRouter with JsonSupport {

  private val globalConfig: Config = ConfigFactory.load()
  private val COOKIE_MAX_AGE = globalConfig.getLong("captive-portal.web.cookie-max-age")
  private val WEB_SERVER_IP = globalConfig.getString("captive-portal.ip.web")

  // format: off
  val route: Route =
    get {
      pathPrefix("captive-portal") {
        path("health-check") {
          complete(HttpResponse(status = StatusCodes.OK, entity = HttpEntity("OK")))
        } ~
        path("") {
          extractParameters {
            case (_, ip, maybeCookie) => {
              val maybeUid = maybeCookie.map(_.value)
              val userStatus = captivePortalService.getOrCreateUserStatus(maybeUid, ip.toOption)
              setCookie(HttpCookie("captive-portal-uid", value = userStatus.uid, maxAge = Some(COOKIE_MAX_AGE))) {
                val content = if (userStatus.authorized) {
                  getContentAndReplaceMacro(Source.fromResource(s"web/authorized.html"))
                } else {
                  getContentAndReplaceMacro(Source.fromResource(s"web/unauthorized.html"))
                }
                content match {
                  case Some(c) => complete(HttpResponse(status = StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, c)))
                  case None => complete(StatusCodes.NotFound)
                }
              }
            }
          }
        } ~
        path("my-status") {
          extractParameters {
            case (_, ip, maybeCookie) => {
              val maybeUid = maybeCookie.map(_.value)
              val userStatus = captivePortalService.getOrCreateUserStatus(maybeUid, ip.toOption)
              complete(userStatus)
            }
          }
        }
      } ~
      path(Remaining) { _ =>
        redirect(s"http://${WEB_SERVER_IP}/captive-portal/", StatusCodes.Found)
      }
    } ~
    post {
      pathPrefix("captive-portal") {
        path("register") {
          extractParameters {
            case (_, ip, maybeCookie) => {
              val maybeUid = maybeCookie.map(_.value)
              val userStatus = captivePortalService.getOrCreateUserStatus(maybeUid, ip.toOption)
              captivePortalService.registerUser(userStatus.uid, ip.toOption)
              redirect(s"http://${WEB_SERVER_IP}/captive-portal/", StatusCodes.Found)
            }
          }
        }
      }
    }
  // format: on

  private def extractParameters(f: (String, RemoteAddress, Option[HttpCookiePair]) => Route): Route = {
    extractHost { host =>
      extractClientIP { ip =>
        optionalCookie("captive-portal-uid") { maybeCookie =>
          f(host, ip, maybeCookie)
        }
      }
    }
  }

  private def getContentAndReplaceMacro(bufferedSource: BufferedSource): Option[String] = {
    val stringTry = Try(bufferedSource.mkString)
    stringTry match {
      case Success(string) => {
        bufferedSource.close()
        val content = string.replaceAll("#FORM_URL#", s"http://${WEB_SERVER_IP}/captive-portal/register")
        Some(content)
      }
      case Failure(_) => None
    }
  }

  private def bufferedSourceToByteArray(bufferedSource: BufferedSource): Option[Array[Byte]] = {
    val readerTry = Try(bufferedSource.bufferedReader())
    readerTry match {
      case Success(reader) => {
        val res = Stream.continually(reader.read).takeWhile(_ != -1).map(_.toByte).toArray
        reader.close()
        Some(res)
      }
      case Failure(_) => None
    }
  }
}
