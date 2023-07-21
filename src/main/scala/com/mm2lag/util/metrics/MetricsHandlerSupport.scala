package com.mm2lag.util.metrics

import com.codahale.metrics.{MetricRegistry, Snapshot}
import io.circe.Json

/**
 * Created by penkov on 02.06.17.
 */
trait MetricsHandlerSupport {

  import scala.jdk.CollectionConverters._


  private def histo(count: Long, h: Snapshot, divider: Int = 1): Json = {
    try {
      Json.obj(
        "p75" -> Json.fromDoubleOrString(h.get75thPercentile() / divider),
        "p95" -> Json.fromDoubleOrString(h.get95thPercentile() / divider),
        "p99" -> Json.fromDoubleOrString(h.get99thPercentile() / divider),
        "p999" -> Json.fromDoubleOrString(h.get999thPercentile() / divider),
        "max" -> Json.fromDoubleOrString(h.getMax / divider),
        "min" -> Json.fromDoubleOrString(h.getMin / divider),
        "mean" -> Json.fromDoubleOrString(h.getMean / divider),
        "stddev" -> Json.fromDoubleOrString(h.getStdDev / divider),
        "med" -> Json.fromDoubleOrString(h.getMedian / divider),
        "count" -> Json.fromLong(count)
      )
    } catch {
      case x: Throwable => Json.obj("error" -> Json.fromString(x.getClass.getName + "_" + x.getMessage))
    }
  }


  protected def collectMetrics(): Json = {
    val registry: MetricRegistry = MetricsHolder.registry
    val counters = registry.getCounters.asScala.map(x => x._1 -> Json.fromLong(x._2.getCount))
    val meters = registry.getMeters.asScala.map(x => x._1 -> Json.fromDoubleOrString(x._2.getOneMinuteRate))
    val gauges = registry.getGauges.asScala.map(x => x._1 -> (x._2.getValue match {
      case n: Long => Json.fromLong(n)
      case n: Int => Json.fromInt(n)
      case n: Double => Json.fromDoubleOrString(n)
      case n: Float => Json.fromDoubleOrString(n.toDouble)
      case n: String => Json.fromString(n)
      case n: Boolean => Json.fromBoolean(n)
      case n => Json.fromString(n.toString)
    }))

    val timers = registry.getTimers.asScala.map { x =>
      x._1 -> histo(x._2.getCount, x._2.getSnapshot, 1000000000)
    }

    val histograms = registry.getHistograms().asScala.map(x => x._1 -> {
      val h = x._2.getSnapshot
      histo(x._2.getCount, h)
    })


    Json.obj(
      "gauges" -> Json.obj(gauges.toSeq.sortBy(_._1): _*),
      "counters" -> Json.obj(counters.toSeq.sortBy(_._1): _*),
      "timers" -> Json.obj(timers.toSeq.sortBy(_._1): _*),
      "meters" -> Json.obj(meters.toSeq.sortBy(_._1): _*),
      "histograms" -> Json.obj(histograms.toSeq.sortBy(_._1): _*)
    )


  }


}
