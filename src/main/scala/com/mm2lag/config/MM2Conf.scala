package com.mm2lag.config

case class TrafficInfo(from: String,
                       to: String,
                       topics: Option[Seq[String]],
                       topicsPatterns: Option[Seq[String]],
                       connectorName: String,
                       mm2OffsetsTopic: String)


case class MM2Conf(clusters: Map[String, Map[String, String]],
                   traffic: Seq[TrafficInfo])
