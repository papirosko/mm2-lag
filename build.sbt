import sbt.Keys._
import scala.collection.JavaConverters._
import scala.sys.process.Process

ThisBuild / version := "0.2.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

scalacOptions ++= Seq("-deprecation", "-feature")


val akkaVersion = "2.7.0"

lazy val root = (project in file("."))
  .settings(
    name := "mm2-lag"
  )
Global / cancelable := true
Global / concurrentRestrictions += Tags.limit(Tags.Test, 1)

Compile / mainClass := Some("com.mm2lag.MM2LagMeter")
Compile / discoveredMainClasses := Nil




libraryDependencies ++= Seq(
  "org.apache.kafka" % "kafka-clients" % "3.4.0",
  "net.codingwell" %% "scala-guice" % "5.1.1",
  "com.github.pureconfig" %% "pureconfig" % "0.17.4",


  "nl.grons" %% "metrics4-scala" % "4.1.9",
  "io.dropwizard.metrics" % "metrics-jvm" % "4.1.12.1",
  "io.prometheus" % "simpleclient_common" % "0.5.0",
  "io.prometheus" % "simpleclient" % "0.5.0",
  "io.prometheus" % "simpleclient_dropwizard" % "0.5.0",

  "org.slf4j" % "slf4j-api" % "2.0.6",
  "org.slf4j" % "jcl-over-slf4j" % "2.0.6",
  "org.slf4j" % "jul-to-slf4j" % "2.0.6",
  "org.slf4j" % "log4j-over-slf4j" % "2.0.6",
  "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.20.0",
  "ch.qos.logback" % "logback-core" % "1.4.7",
  "ch.qos.logback" % "logback-classic" % "1.4.7",

  "org.scalatest" %% "scalatest" % "3.2.15" % Test,

  "com.github.swagger-akka-http" %% "swagger-akka-http" % "2.10.0",
  "io.swagger.core.v3" % "swagger-annotations-jakarta" % "2.2.10",
  "io.swagger.core.v3" % "swagger-jaxrs2-jakarta" % "2.2.10",
  "jakarta.ws.rs" % "jakarta.ws.rs-api" % "3.1.0",

  "com.typesafe" % "config" % "1.4.2",

  "io.prometheus" % "simpleclient_common" % "0.16.0",
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_dropwizard" % "0.16.0",

  "io.circe" %% "circe-yaml" % "0.15.0-RC1",
  "io.circe" %% "circe-generic" % "0.14.5",
  "io.circe" %% "circe-parser" % "0.14.5",

  "org.webjars" % "webjars-locator" % "0.46",
  "org.webjars" % "swagger-ui" % "4.18.1"

)

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

lazy val env = System.getenv().asScala

lazy val gitBranch = {
  env.get("CI_COMMIT_REF_NAME").map(br => {
    println("Using gitBranch from gitlab CI: " + br)
    br
  }).orElse(env.get("GIT_BRANCH").map(br => {
    val b = br.split('/').drop(1).mkString("/")
    println("Using gitBranch from environment: " + b)
    b
  })).getOrElse {
    val ver: String = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
    println("Using gitBranch from local repository: " + ver)
    ver
  }
}


lazy val dockerRegistry = env.getOrElse("DOCKER_REGISTRY", "")

Docker / packageName := env.get("CI_REGISTRY_IMAGE").map { v =>
  if (v.startsWith(dockerRegistry)) {
    v.substring(dockerRegistry.length + 1) // '/' in the end
  } else {
    v
  }
}.getOrElse(name.value)
dockerBaseImage := "openjdk:17-jdk-slim"
dockerExposedPorts := Seq(8080)
dockerAlias := dockerAlias.value.withTag(
  env.get("DOCKER_TAG")
    .orElse(env.get("CI_COMMIT_SHORT_SHA"))
    .orElse(env.getOrElse("CI_COMMIT_REF_SLUG", gitBranch) match {
      case "main" => Some("latest")
      case other => Some(other)
    })
)
dockerRepository := Some(dockerRegistry).filter(_ != "")
daemonUser := "docker"
