import sbt._

object Dependencies {
  lazy val akkaStream = "com.typesafe.akka"                %% "akka-stream"           % "2.5.23"
  lazy val akkaHttp = "com.typesafe.akka"                  %% "akka-http"             % "10.1.8"
  lazy val sprayJson = "com.typesafe.akka"                 %% "akka-http-spray-json"  % "10.1.8"
  lazy val caffeineCache = "com.github.ben-manes.caffeine" %  "caffeine"              % "2.7.0"
  lazy val scalaLogging = "com.typesafe.scala-logging"     %% "scala-logging"         % "3.9.2"
  lazy val redis = "net.debasishg"                         %% "redisclient"           % "3.10"
  lazy val dogstatsd = "com.datadoghq"                     %  "java-dogstatsd-client" % "2.3"
  lazy val scalaTest = "org.scalatest"                     %% "scalatest"             % "3.0.8"

  // dependencies of OpenFlowJ Loxi version 3.5.535
  lazy val findBugsAnnotations = "com.google.code.findbugs" % "annotations"     % "3.0.1u2"
  lazy val guava = "com.google.guava"                       % "guava"           % "20.0"
  lazy val logbackClassic = "ch.qos.logback"                % "logback-classic" % "1.2.3"
  lazy val logbackCore = "ch.qos.logback"                   % "logback-core"    % "1.2.3"
  lazy val netty = "io.netty"                               % "netty-all"       % "4.1.17.Final"
  lazy val slf4jApi = "org.slf4j"                           % "slf4j-api"       % "1.7.25"
}
