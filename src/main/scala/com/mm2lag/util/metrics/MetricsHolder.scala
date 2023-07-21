package com.mm2lag.util.metrics

import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.jvm.{GarbageCollectorMetricSet, MemoryUsageGaugeSet}
import nl.grons.metrics4.scala.MetricName

import java.lang.management.{ManagementFactory, ThreadMXBean}

object MetricsHolder extends MetricsSupport {
  val registry = new MetricRegistry()
  val startTime: Long = System.currentTimeMillis()
  val threads: ThreadMXBean = ManagementFactory.getThreadMXBean

  registry.registerAll(new MemoryUsageGaugeSet())
  registry.registerAll(new GarbageCollectorMetricSet())
  registry.registerAll(CpuGaugeSet.create)

  gauge("uptime") {
    System.currentTimeMillis() - startTime
  }

  gauge("threadsCount") {
    threads.getThreadCount
  }

  gauge("peakThreadsCount") {
    threads.getPeakThreadCount
  }

  def setVersion(s: String): Unit = {
    gauge("version") {
      s
    }
  }

  override lazy val metricBaseName: MetricName = MetricName("sys")
}
