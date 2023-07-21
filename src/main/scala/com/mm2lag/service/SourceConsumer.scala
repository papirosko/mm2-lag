package com.mm2lag.service

import com.mm2lag.util.Loggable
import com.mm2lag.util.metrics.MetricsSupport
import com.mm2lag.{ClusterAlias, PartitionKey, PartitionOffsetInfo, TopicName}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException

import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.{ListHasAsScala, MapHasAsJava, MapHasAsScala, SeqHasAsJava}
import scala.util.control.NonFatal

class SourceConsumer(val name: ClusterAlias,
                     kafkaProperties: Map[String, String],
                     topics: Iterable[String],
                     offsetsStore: OffsetsStore) extends Loggable with MetricsSupport {

  private val running = new AtomicBoolean(false)
  private val properties = new Properties()
  properties.putAll(kafkaProperties.asJava)
  properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
  properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
  properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
  properties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
  properties.put(ConsumerConfig.GROUP_ID_CONFIG, "mm2-lag-meter");

  private val consumer = new KafkaConsumer[String, String](properties)

  private val thread: Thread = new Thread() {
    override def run(): Unit = {

      log.info(s"Collecting offset in kafka cluster ${name.name} from topics: ${topics.mkString(", ")}.")

      while (running.get()) {
        try {
          runImpl()
          Thread.sleep(2000)
        } catch {
          case _: WakeupException =>
            log.trace(s"Consumer ${name.name} waked up")

          case NonFatal(e) =>
            log.warn(s"Failed to process records from ${name}", e)
        }
      }
    }
  }

  private def runImpl(): Unit = {
    val offsets = topics
      .map { topic =>
        consumer.partitionsFor(topic)
      }
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
