package com.mm2lag.service

import com.mm2lag.config.AppConf
import com.mm2lag.util.Loggable
import com.mm2lag.util.metrics.MetricsSupport
import com.mm2lag.{ClusterAlias, PartitionKey, PartitionOffsetInfo, TopicName}
import io.circe.generic.auto.exportDecoder
import io.circe.parser._
import io.circe.{DecodingFailure, Json}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException

import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters.{IteratorHasAsScala, ListHasAsScala, MapHasAsJava, SeqHasAsJava}
import scala.util.control.NonFatal

class TargetConsumer(val name: ClusterAlias,
                     kafkaProperties: Map[String, String],
                     offsetTopicName: String,
                     watchConnectors: Set[String],
                     offsetsStore: OffsetsStore,
                     appConf: AppConf) extends Loggable with MetricsSupport {

  private val running = new AtomicBoolean(false)
  private val properties = new Properties()
  properties.putAll(kafkaProperties.asJava)
  properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
  properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
  properties.put(ConsumerConfig.GROUP_ID_CONFIG, appConf.kafka.consumerGroup)
  properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
  properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")
  properties.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "100")

  private val consumer = new KafkaConsumer[String, String](properties)

  private val thread: Thread = new Thread() {
    override def run(): Unit = {
      val partitions = consumer.partitionsFor(offsetTopicName)
      val topicPartitions = partitions.asScala.map(p => new TopicPartition(p.topic(), p.partition())).asJava
      consumer.assign(topicPartitions)
      consumer.seekToBeginning(topicPartitions)
      log.info(s"Collecting offset in kafka cluster ${name.name} from topic $offsetTopicName as " +
        s"a consumer group='${appConf.kafka.consumerGroup}'. " +
        s"Watching ${partitions.size()} " +
        s"partitions. Will only use entries from connectors: ${watchConnectors.mkString(", ")}")

      while (running.get()) {
        try {
          runImpl()
        } catch {
          case _: WakeupException =>
            log.trace(s"Consumer ${name.name} waked up")

          case NonFatal(e) =>
            log.warn(s"Failed to process records from $name", e)
        }

      }
    }
  }

  private def runImpl(): Unit = {
    val records = consumer.poll(Duration.ofMillis(1000))
    val consumerRecords = records.iterator().asScala.toIndexedSeq
    counter(s"${name.name}_fetched").inc(consumerRecords.size)

    consumerRecords.foreach { record =>
      try {
        val keyJson = parse(record.key()).getOrElse(Json.arr()).asArray.getOrElse(Vector.empty)
        val connectorName = keyJson.headOption.flatMap(_.asString)

        if (connectorName.exists(name => watchConnectors.contains(name))) {

          val jsonValues = for (
            pInfo <- keyJson.drop(1).headOption.map(_.as[PartitionMMKey])
              .getOrElse(Left(DecodingFailure("Not enough elements in key json array", Nil)));
            offset <- parse(record.value()).flatMap(_.as[OffsetInfo])
          ) yield (pInfo, offset)

          jsonValues match {
            case Right((pInfo, offset)) =>
              offsetsStore.submitTargetOffset(PartitionOffsetInfo(
                key = PartitionKey(
                  topic = TopicName(pInfo.topic),
                  partition = pInfo.partition,
                  clusterAlias = ClusterAlias(pInfo.cluster)
                ),
                offset = offset.offset
              ))

            case Left(error) =>
              log.warn(s"Failed to decode record $record", error)
          }

        }
      } catch {
        case e: Exception =>
          log.warn(s"Failed to process record $record", e)
      }

    }

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


// for json decoders
case class PartitionMMKey(topic: String, partition: Int, cluster: String)

case class OffsetInfo(offset: Long)
