package com.mm2lag.service

import com.mm2lag.util.metrics.MetricsSupport
import com.mm2lag.{ClusterAlias, ClusterTopic, PartitionKey, PartitionOffsetInfo, TopicName}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.jdk.CollectionConverters.SetHasAsScala

class OffsetsStore extends MetricsSupport {



  private val sourceOffsets = new AtomicReference[Map[ClusterAlias, Map[TopicName, Seq[PartitionOffsetInfo]]]](Map.empty)
  private val targetOffsets = new ConcurrentHashMap[PartitionKey, Long]()


  def submitSourceOffsets(offsets: Iterable[PartitionOffsetInfo]): Unit = timer("submitSourceOffsets").time {
    val grouped = offsets.groupBy(o => ClusterTopic(o.key.clusterAlias, o.key.topic))
    val remove = grouped.keySet
    sourceOffsets.updateAndGet { data =>
      // clear removed topics
      val dataCleared = remove.groupMap(_.cluster)(_.topic).foldLeft(data) { case (acc, (cluster, topics)) =>
        val existingTopics = acc.getOrElse(cluster, Map.empty)
        val updated = existingTopics.removedAll(topics)
        acc.updated(cluster, updated)
      }

      // add new offsets
      grouped.foldLeft(dataCleared) { case (acc, (clusterTopic, offsets)) =>
        val existingTopics = acc.getOrElse(clusterTopic.cluster, Map.empty)
        val updatedTopics = existingTopics.updated(clusterTopic.topic, offsets.toSeq)
        acc.updated(clusterTopic.cluster, updatedTopics)
      }
    }
  }


  def submitTargetOffset(partitionOffsetInfo: PartitionOffsetInfo): Unit = timer("submitTargetOffset").time {
    targetOffsets.put(partitionOffsetInfo.key, partitionOffsetInfo.offset)
  }


  def offsetsForCluster(cluster: ClusterAlias): Seq[PartitionOffsetInfo] = {
    val state = sourceOffsets.get
    state.getOrElse(cluster, Map.empty).values.flatten.toSeq
  }

  def targetForCluster(cluster: ClusterAlias): Seq[PartitionOffsetInfo] = {
    targetOffsets.entrySet().asScala.filter(_.getKey.clusterAlias == cluster)
      .map(x => PartitionOffsetInfo(key = x.getKey, offset = x.getValue)).toSeq
  }

}
