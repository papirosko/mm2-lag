# Clusters configuration
All configuration should be stored in a file `clusters.yaml`

Check `cluster-demo.yaml`. It contains 2 main sections.
* `clusters` - describe each cluster. You should set cluster alias as a name of the property of `clusters` object.
Put all kafka properties as a fields of your alias.
* `traffic` - an array that defines how data is copied between the clusters. For each flow
set source cluster alias in `from` and target cluster alias in `target`

`connectorName` should be the name of the connector with 
`"connector.class": "org.apache.kafka.connect.mirror.MirrorSourceConnector"`. This property will be used
to collect offsets from the mm2 offsets topic.

`mm2OffsetsTopic` - the name of the topic, used by mm2 to store offsets


# Run application

Checkout the sources:
```shell
git clone https://github.com/papirosko/mm2-lag.git
cd mm2-lag
```
Create `clusters.yaml`.

Compile and run the application:
```shell
sbt clean stage
./target/universal/stage/bin/mm2-lag
```

Open http://localhost:8080. Check [metrics](http://localhost:8080/metrics) endpoint, take a look at
`counters` section, you should see `SourceConsumer` and `TargetConsumer` counters increasing for each cluster.


Also you can build archive with the application:
```shell
sbt clean universal:packageBin
```

You can override the cluster file location with environment variable `CLUSTERS_FILE`:
```shell
CLUSTERS_FILE="/etc/clusters.yaml" ./target/universal/stage/bin/mm2-lag
```


# Lag values
Use `/api/lag_per_topic` endpoint to see lag values per each topic in text format.

Use `/api/prometheus_lag` to see all lags per partition for all clusters. This endpoint can be used for prometheus 
to grab the metrics.

