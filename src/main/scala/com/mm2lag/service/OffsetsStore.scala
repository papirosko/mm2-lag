package com.mm2lag.service

import com.mm2lag._
import com.mm2lag.util.metrics.MetricsSupport

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import scala.jdk.CollectionConverters.SetHasAsScala

class OffsetsStore extends MetricsSupport {


  private val sourceOffsets = new AtomicReference[Map[ClusterAlias, Map[TopicName, Seq[PartitionOffsetInfo]]]](Map.empty)
  private val targetOffsets = new ConcurrentHashMap[PartitionKey, Long]()
  private val lagOnCommitTime = new ConcurrentHashMap[PartitionKey, Long]()
  private val lagWriteLock = new ReentrantLock()

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

    try {
      lagWriteLock.lock()
      // if there is no lag for this partition, then maybe it means that
      // target offset was committed before source. In this case we need to
      // recalc the lag with the fresh data
      offsets.foreach { sourceOffset =>
        Option(targetOffsets.get(sourceOffset.key))
          .map(targetOffset => sourceOffset.offset - targetOffset)
          .filter(_ >= 0)
          .foreach { lag =>
            lagOnCommitTime.putIfAbsent(sourceOffset.key, lag)
          }
      }
    } finally {
      lagWriteLock.unlock()
    }
  }


  def submitTargetOffset(partitionOffsetInfo: PartitionOffsetInfo): Unit = timer("submitTargetOffset").time {
    targetOffsets.put(partitionOffsetInfo.key, partitionOffsetInfo.offset)

    try {
      lagWriteLock.lock()
      val currentSourceOffset = sourceOffsets.get()
        .get(partitionOffsetInfo.key.clusterAlias)
        .flatMap(clusterTopics => clusterTopics.get(partitionOffsetInfo.key.topic))
        .flatMap(topicPartitions => topicPartitions.find(_.key == partitionOffsetInfo.key))
        .map(_.offset)

      currentSourceOffset match {
        case Some(sourceOffset) if sourceOffset >= partitionOffsetInfo.offset =>
          lagOnCommitTime.put(partitionOffsetInfo.key, sourceOffset - partitionOffsetInfo.offset)

        case Some(sourceOffset) if sourceOffset < partitionOffsetInfo.offset =>
          // here the target consumer committed offsets before source consumer on the same partition.
          // we will wait update the lag when source consumer will get the actual data
          lagOnCommitTime.remove(partitionOffsetInfo.key)

        case _ =>
      }
    } finally {
      lagWriteLock.unlock()
    }


  }


  def offsetsForCluster(cluster: ClusterAlias): Seq[PartitionOffsetInfo] = {
    val state = sourceOffsets.get
    state.getOrElse(cluster, Map.empty).values.flatten.toSeq
  }

  def targetForCluster(cluster: ClusterAlias): Seq[PartitionOffsetInfo] = {
    targetOffsets.entrySet().asScala.filter(_.getKey.clusterAlias == cluster)
      .map(x => PartitionOffsetInfo(key = x.getKey, offset = x.getValue)).toSeq
  }

  def lagForPartition(key: PartitionKey): Option[Long] = {
    Option(lagOnCommitTime.get(key))
  }

}
