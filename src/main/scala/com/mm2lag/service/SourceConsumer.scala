package com.mm2lag.service

import com.mm2lag.config.AppConf
import com.mm2lag.util.{Loggable, PatternsMatcher}
import com.mm2lag.util.metrics.MetricsSupport
import com.mm2lag.{ClusterAlias, PartitionKey, PartitionOffsetInfo, TopicName}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException

import java.util.Properties
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava, MapHasAsScala, SeqHasAsJava}
import scala.util.control.NonFatal

class SourceConsumer(name: ClusterAlias,
                     kafkaProperties: Map[String, String],
                     topics: Iterable[String],
                     topicsPatterns: Iterable[String],
                     offsetsStore: OffsetsStore,
                     appConf: AppConf) extends Loggable with MetricsSupport {

  private val SLEEP_TIMEOUT = 2.seconds


  private val explicitTopicsNames = topics.toSet
  private val patternsMatcher = new PatternsMatcher(topicsPatterns)

  private val running = new AtomicBoolean(false)
  private val properties = new Properties()
  properties.putAll(kafkaProperties.asJava)
  properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
  properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
  properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
  properties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000")
  properties.put(ConsumerConfig.GROUP_ID_CONFIG, appConf.kafka.consumerGroup)

  private val consumer = new KafkaConsumer[String, String](properties)
  private val processedTopics = new AtomicReference[Iterable[String]](explicitTopicsNames)

  gauge(s"processed_topics.${name.name}")(processedTopics.get().toSeq.sorted.mkString(","))

  private val thread: Thread = new Thread() {
    override def run(): Unit = {

      log.info(s"Collecting offset in kafka cluster ${name.name} from topics: [${topics.mkString(", ")}]" +
        s" and topic patterns: [${topicsPatterns.map(p => s"'$p'").mkString(", ")}]" +
        s" as a consumer group='${appConf.kafka.consumerGroup}'.")

      while (running.get()) {
        try {
          runImpl()
          Thread.sleep(SLEEP_TIMEOUT.toMillis)
        } catch {
          case _: WakeupException =>
            log.trace(s"Consumer ${name.name} waked up")

          case NonFatal(e) =>
            log.warn(s"Failed to process records from $name", e)
        }
      }
    }
  }


  private def runImpl(): Unit = timer(s"consume_offsets.${name.name}").time {

    val topicPartitions = if (topicsPatterns.nonEmpty) {
      val topicPartitions = consumer.listTopics().asScala
        .filter { case (topic, partitions) =>
          (patternsMatcher.matches(topic) || explicitTopicsNames.contains(topic)) && partitions.size() > 0
        }
      processedTopics.set(topicPartitions.keys)
      topicPartitions.values
    } else {
      topics.map(consumer.partitionsFor)
    }


    val offsets = topicPartitions
      .map { partitions =>
        consumer.endOffsets(partitions.asScala.map(p =>
          new TopicPartition(p.topic(), p.partition())).asJava
        )
      }
      .flatMap { partitionsWithOffsets =>
        partitionsWithOffsets.asScala.map { case (p, offset) =>
          PartitionOffsetInfo(
            key = PartitionKey(
              clusterAlias = name,
              topic = TopicName(p.topic()),
              partition = p.partition()
            ),
            offset = offset
          )
        }
      }.toIndexedSeq

    counter(s"${name.name}_fetched").inc(offsets.size)
    offsetsStore.submitSourceOffsets(offsets)
  }

  def start(): Unit = {
    running.set(true)
    thread.start()
  }


  def stop(): Unit = {
    running.set(false)
    consumer.wakeup()
    thread.join()
  }

}
