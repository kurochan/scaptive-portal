import Dependencies._

ThisBuild / scalaVersion := "2.12.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "org.kurochan"
ThisBuild / organizationName := "scaptive-portal"

lazy val root = (project in file(".")).settings(
  name := "scaptive-portal",
  libraryDependencies ++= Seq(
    akkaStream,
    akkaHttp,
    sprayJson,
    caffeineCache,
    redis,
    dogstatsd,
    scalaLogging,
    scalaTest % Test,
    // dependencies of OpenFlowJ Loxi version 3.5.535
    findBugsAnnotations,
    guava,
    logbackClassic,
    logbackCore,
    netty,
    slf4jApi
  )
)
