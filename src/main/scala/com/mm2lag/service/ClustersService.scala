package com.mm2lag.service

import com.mm2lag.config.MM2Conf
import com.mm2lag.util.Loggable

import java.io.Reader
import scala.io.Source
import scala.util.Using

class ClustersService extends Loggable {

  import io.circe.generic.auto._

  private var clusters: MM2Conf = _

  def loadFromFile(filename: String): Unit = {
    log.info(s"Loading clusters from $filename")
    clusters = Using.resource(Source.fromFile(filename)) { in =>
      val lines = in.getLines().toIndexedSeq
      loadImpl(lines.mkString("\n"))
    }
  }

  def validate(): Either[String, MM2Conf] = {
    val sources = clusters.traffic.map(_.from).toSet
    val targets = clusters.traffic.map(_.to).toSet

//    val missingSources = sources.filter(s => !clusters.clusters.contains(s))
//    val missingSourceTopics = sources.flatMap(s => clusters.clusters.get(s).map(i => s -> i))
//      .filter { case (_, clusterInfo) => clusterInfo.topics.getOrElse(Nil).isEmpty }
//      .map(_._1)
//    val missingTargets = targets.filter(s => !clusters.clusters.contains(s))


//    if (missingSources.nonEmpty) {
//      Left(s"No configuration for clusters: ${missingSources.mkString(", ")}")
//    } else if (missingSourceTopics.nonEmpty) {
//      Left(s"No topics for clusters: ${missingSourceTopics.mkString(", ")}")
//    } else if (missingTargets.nonEmpty) {
//      Left(s"No configuration for clusters: ${missingTargets.mkString(", ")}")
//    } else {
//      Right(clusters)
//    }

    Right(clusters)

  }

  def clustersInfo: MM2Conf = clusters

  private def loadImpl(in: String): MM2Conf = {
    val mm2Conf = io.circe.yaml.parser.parse(in)
      .flatMap(_.as[MM2Conf])

    mm2Conf match {
      case Right(items) => items

      case Left(e) =>
        log.error("Failed to load clusters config", e)
        throw new RuntimeException("Can't load clusters config", e)
    }

  }


}
