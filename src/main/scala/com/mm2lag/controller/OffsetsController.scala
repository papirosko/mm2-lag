package com.mm2lag.controller

import akka.http.scaladsl.model.{ContentTypes, HttpCharsets, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{Directives, Route}
import com.google.inject.Inject
import com.mm2lag.ClusterAlias
import com.mm2lag.service.{ClustersService, OffsetsStore}
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.{GET, Path, Produces}

@Path("/api")
@Tag(name = "offsets")
class OffsetsController @Inject()(offsetsStore: OffsetsStore,
                                  clustersService: ClustersService) extends Directives {

  val route: Route = sourceForClusterPerTopic ~ targetForClusterPerTopic ~ lagPerTopic ~ prometheusLag

  @GET()
  @Path("source_offsets_per_topic")
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Parameter(
    name = "cluster",
    in = ParameterIn.QUERY,
    description = "Cluster alias",
    required = true,
    content = Array(new Content(schema = new Schema(`type` = "String")))
  )
  @Operation(summary = "offsets per topic")
  def sourceForClusterPerTopic: Route = path("api" / "source_offsets_per_topic") {
    get {
      parameter("cluster") { cluster =>
        complete {
          val offsets = offsetsStore.offsetsForCluster(ClusterAlias(cluster))
          val perTopic = offsets.groupBy(_.key.topic)
          HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            perTopic.iterator.toSeq.sortBy(_._1.name).map { case (topic, partitions) =>
              partitions.sortBy(_.key.partition)
                .map(p => s"${topic.name}/${p.key.partition}: ${p.offset}")
                .mkString("\n")
            }.mkString("\n")
          )
        }
      }
    }
  }

  @GET()
  @Path("target_offsets_per_topic")
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Parameter(
    name = "cluster",
    in = ParameterIn.QUERY,
    description = "Cluster alias",
    required = true,
    content = Array(new Content(schema = new Schema(`type` = "String")))
  )
  @Operation(summary = "offsets per topic")
  def targetForClusterPerTopic: Route = path("api" / "target_offsets_per_topic") {
    get {
      parameter("cluster") { cluster =>
        complete {
          val offsets = offsetsStore.targetForCluster(ClusterAlias(cluster))
          val perTopic = offsets.groupBy(_.key.topic)
          HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            perTopic.iterator.toSeq.sortBy(_._1.name).map { case (topic, partitions) =>
              partitions.sortBy(_.key.partition)
                .map(p => s"${topic.name}/${p.key.partition}: ${p.offset}")
                .mkString("\n")
            }.mkString("\n")
          )
        }
      }
    }
  }

  @GET()
  @Path("lag_per_topic")
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Parameter(
    name = "cluster",
    in = ParameterIn.QUERY,
    description = "Cluster alias",
    required = true,
    content = Array(new Content(schema = new Schema(`type` = "String")))
  )
  @Operation(summary = "offsets per topic")
  def lagPerTopic: Route = path("api" / "lag_per_topic") {
    get {
      parameter("cluster") { cluster =>
        complete {
          val sourceOffsets = offsetsStore.offsetsForCluster(ClusterAlias(cluster))
          val targetOffsets = offsetsStore.targetForCluster(ClusterAlias(cluster))
            .groupMapReduce(_.key)(_.offset) { case (a, _) => a }

          val perTopic = sourceOffsets.groupBy(_.key.topic).map { case (topic, sourcePartitions) =>
            topic -> sourcePartitions.map { p =>
              val targetOffset = targetOffsets.getOrElse(p.key, 0L)
              p.offset - targetOffset
            }.sum
          }
          HttpEntity(
            ContentTypes.`text/plain(UTF-8)`,
            perTopic.toSeq.sortBy(_._1.name).map { case (topic, lag) =>
              s"${topic.name}: $lag"
            }.mkString("\n")
          )
        }
      }
    }
  }


  @GET()
  @Path("prometheus_lag")
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Operation(summary = "lags in prometheus format for all source clusters")
  def prometheusLag(): Route = path("api" / "prometheus_lag") {
    get {
      complete {

        val perPartition = clustersService.clustersInfo.traffic.map(_.from).distinct.flatMap { sourceCluster =>

          val sourceOffsets = offsetsStore.offsetsForCluster(ClusterAlias(sourceCluster))
          val targetOffsets = offsetsStore.targetForCluster(ClusterAlias(sourceCluster))
            .groupMapReduce(_.key)(_.offset) { case (a, _) => a }

          sourceOffsets.map { partition =>
            val targetOffset = targetOffsets.getOrElse(partition.key, 0L)
            partition.key -> (partition.offset - targetOffset)
          }

        }.sortBy(x => (x._1.clusterAlias.name, x._1.topic.name, x._1.partition))

        val export = perPartition.map { case (partition, lag) =>
          s"""# HELP mm2_lag
             |# TYPE mm2_lag gauge
             |mm2_lag{cluster="${partition.clusterAlias.name}",topic="${partition.topic.name}",partition="${partition.partition}"} $lag
             |""".stripMargin
        }.mkString("\n")

        HttpEntity(
          MediaTypes.`text/plain` withParams Map("version" -> "0.0.4") withCharset HttpCharsets.`UTF-8`,
          export
        )

      }
    }
  }

}
