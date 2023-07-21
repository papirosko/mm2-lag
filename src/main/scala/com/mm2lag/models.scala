package com.mm2lag

object models {}

case class ClusterAlias(name: String) extends AnyVal
case class TopicName(name: String) extends AnyVal
case class ClusterTopic(cluster: ClusterAlias, topic: TopicName)

case class PartitionKey(clusterAlias: ClusterAlias,
                        topic: TopicName,
                        partition: Int)

case class PartitionOffsetInfo(key: PartitionKey,
                               offset: Long)

