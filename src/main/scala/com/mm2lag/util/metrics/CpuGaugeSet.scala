package com.mm2lag.util.metrics

import com.codahale.metrics.{Gauge, Metric, MetricSet}

import java.lang.management.{ManagementFactory, OperatingSystemMXBean}
import java.util.Collections

object CpuGaugeSet {
  def create = new CpuGaugeSet(ManagementFactory.getOperatingSystemMXBean)
}

class CpuGaugeSet private(val operatingSystemMXBean: OperatingSystemMXBean) extends MetricSet {
  def getMetrics: java.util.Map[String, Metric] = {
    if (!operatingSystemMXBean.isInstanceOf[com.sun.management.OperatingSystemMXBean])
      return Collections.emptyMap

    val osMxBean = operatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]
    val gauges = new java.util.HashMap[String, Metric]
    gauges.put("process-cpu-load-percentage", new Gauge[Double]() {
      def getValue: Double = Option(osMxBean.getProcessCpuLoad).getOrElse(0L)
    })
    gauges.put("system-cpu-load-percentage", new Gauge[Double]() {
      def getValue: Double = Option(osMxBean.getSystemCpuLoad).getOrElse(0L)
    })
    gauges.put("system-load-average", new Gauge[Double]() {
      def getValue: Double = Option(osMxBean.getSystemLoadAverage).getOrElse(0L)
    })
    gauges.put("process-cpu-time", new Gauge[Long]() {
      def getValue: Long = Option(osMxBean.getProcessCpuTime).getOrElse(0L)
    })
    Collections.unmodifiableMap(gauges)
  }
}
