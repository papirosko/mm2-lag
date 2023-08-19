package com.mm2lag

import akka.actor.ActorSystem
import com.google.inject.{Guice, Inject}
import com.mm2lag.config.AppConf
import com.mm2lag.service.{ClustersService, HttpServer, OffsetsStore, SourceConsumer, TargetConsumer}
import com.mm2lag.util.Loggable
import com.typesafe.config.ConfigFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt


object MM2LagMeter extends App with Loggable {

  import net.codingwell.scalaguice.InjectorExtensions._

  SLF4JBridgeHandler.install()
  log.info("Starting application")

  private val conf = ConfigFactory.load()

  ConfigSource.fromConfig(conf).load[AppConf] match {
    case Right(applicationConf) =>
      val server: MM2LagMeter =
        try {
          val system = ActorSystem("MM2Lag", conf)
          val injector = Guice.createInjector(new AppModule(applicationConf, system))
          injector.instance[MM2LagMeter]
        } catch {
          case e: Exception =>
            log.error("Failed to start application", e)
            System.exit(255)
            null
        }


      Runtime.getRuntime.addShutdownHook(new Thread() {

        override def run(): Unit = {
          log.info("Stopping server")
          server.stop()
        }

      })
      server.start()

    case Left(error) =>
      log.error(s"Failed to read application config: $error")
  }


}


class MM2LagMeter @Inject()(conf: AppConf,
                            clustersService: ClustersService,
                            offsetsStore: OffsetsStore,
                            httpServer: HttpServer) extends Loggable {

  private var sourceConsumers: Seq[SourceConsumer] = Nil
  private var targetConsumers: Seq[TargetConsumer] = Nil

  def start(): Unit = {
    clustersService.loadFromFile(conf.clustersFile)
    log.info(s"Loaded ${clustersService.clustersInfo.clusters.size} clusters")

    clustersService.validate() match {
      case Left(error) =>
        log.error("Clusters configuration contains errors: " + error)

      case Right(clusters) =>
        log.info("Loaded clusters: \n" + clusters.clusters.map { case (clusterAlias, clusterInfo) =>
          s" - $clusterAlias:\n" + clusterInfo.toSeq.sortBy(_._1).map { case (param, value) =>
            val masked = if (param.startsWith("ssl") || param.startsWith("sasl")) "***" else value
            s"   - $param: $masked"
          }.mkString("\n")
        }.mkString("\n"))


        Await.result(httpServer.start(), 10.seconds)

        sourceConsumers = clusters.traffic.map(_.from).map { sourceCluster =>
          val info = clusters.clusters(sourceCluster)
          val watchedTopics = clusters.traffic.filter(_.from == sourceCluster).flatMap(_.topics).toSet
          new SourceConsumer(ClusterAlias(sourceCluster), info, watchedTopics, offsetsStore, conf)
        }

        targetConsumers = clusters.traffic.groupBy(x => x.to -> x.mm2OffsetsTopic)
          .map { case ((targetCluster, offsetTopic), routes) =>
          val info = clusters.clusters(targetCluster)
          new TargetConsumer(
            ClusterAlias(targetCluster), info, offsetTopic, routes.map(_.connectorName).toSet, offsetsStore, conf)
        }.toSeq

        sourceConsumers.foreach(_.start())
        targetConsumers.foreach(_.start())

    }

  }

  def stop(): Unit = {
    targetConsumers.foreach(_.stop())
    sourceConsumers.foreach(_.stop())
    httpServer.stop()
  }

}
