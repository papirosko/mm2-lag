package com.mm2lag.config

case class KafkaConf(consumerGroup: String)

case class HttpConf(host: String, port: Int)

case class AppConf(clustersFile: String,
                   http: HttpConf,
                   kafka: KafkaConf)
