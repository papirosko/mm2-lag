package com.mm2lag.util.metrics

import com.codahale.metrics.{Metric, MetricRegistry, CachedGauge => DropwizardCachedGauge, Gauge => DropwizardGauge, Timer => DropwizardTimer}
import nl.grons.metrics4.scala._
import org.apache.commons.lang3.StringUtils

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.FiniteDuration


trait MetricsSupport {
  protected lazy val metricBaseName: MetricName = MetricName(getClass.getSimpleName)

  protected def metricsRegistry: MetricRegistry = MetricsHolder.registry

  private[this] val gauges: AtomicReference[Seq[DropwizardGauge[_]]] = new AtomicReference(Seq.empty)


  protected def fromDecimal(d: Int): Long = {
    d * 1000000000
  }

  /**
   * Registers a new gauge metric.
   *
   * @param name  the name of the gauge
   * @param scope the scope of the gauge or null for no scope
   */
  protected def gauge[A](name: String, scope: String = null)(f: => A): Gauge[A] = {
    wrapDwGauge(metricNameFor(name, scope), new DropwizardGauge[A] {
      def getValue: A = f
    })
  }


  /**
   * Registers a new gauge metric that caches its value for a given duration.
   *
   * @param name    the name of the gauge
   * @param timeout the timeout
   * @param scope   the scope of the gauge or null for no scope
   */
  protected def cachedGauge[A](name: String, timeout: FiniteDuration, scope: String = null)(f: => A): Gauge[A] = {
    wrapDwGauge(metricNameFor(name, scope), new DropwizardCachedGauge[A](timeout.length, timeout.unit) {
      def loadValue: A = f
    })
  }

  private def wrapDwGauge[A](name: String, handler: => DropwizardGauge[A]): Gauge[A] = {
    Option(metricsRegistry.getGauges.get(name)).fold {
      val dwGauge = handler
      metricsRegistry.register(name, dwGauge)
      gauges.getAndUpdate((t: Seq[DropwizardGauge[_]]) => t :+ dwGauge)
      new Gauge[A](dwGauge)
    } { dwGauge =>
      new Gauge[A](dwGauge.asInstanceOf[DropwizardGauge[A]])
    }
  }

  /**
   * Creates a new counter metric.
   *
   * @param name  the name of the counter
   * @param scope the scope of the counter or null for no scope
   */
  protected def counter(name: String, scope: String = null): Counter = Option(metricsRegistry.getCounters().get(name)).fold {
    new Counter(metricsRegistry.counter(metricNameFor(name, scope)))
  } {
    new Counter(_)
  }


  /**
   * Creates a new histogram metric.
   *
   * @param name  the name of the histogram
   * @param scope the scope of the histogram or null for no scope
   */
  protected def histogram(name: String, scope: String = null): Histogram = Option(metricsRegistry.getHistograms.get(name)).fold {
    new Histogram(metricsRegistry.histogram(metricNameFor(name, scope)))
  } {
    new Histogram(_)
  }

  /**
   * Creates a new meter metric.
   *
   * @param name  the name of the meter
   * @param scope the scope of the meter or null for no scope
   */
  protected def meter(name: String, scope: String = null): Meter = Option(metricsRegistry.getMeters.get(name)).fold {
    new Meter(metricsRegistry.meter(metricNameFor(name, scope)))
  } {
    new Meter(_)
  }

  /**
   * Creates a new timer metric.
   *
   * @param name  the name of the timer
   * @param scope the scope of the timer or null for no scope
   */
  protected def timer(name: String, scope: String = null): TimerExt = Option(metricsRegistry.getTimers.get(name)).fold {
    new TimerExt(metricsRegistry.timer(metricNameFor(name, scope)))
  } {
    new TimerExt(_)
  }


  /**
   * Unregisters all gauges that were created through this builder.
   */
  protected def unregisterGauges(): Unit = {
    val toUnregister = gauges.getAndUpdate((_: Seq[DropwizardGauge[_]]) => Seq.empty)
    metricsRegistry.removeMatching((_: String, metric: Metric) =>
      metric.isInstanceOf[DropwizardGauge[_]] && toUnregister.contains(metric)
    )
  }

  protected def metricNameFor(name: String, scope: String = null): String = {
    StringUtils.replace(metricBaseName.append(name, scope).name, "$", "")
  }


}

class TimerExt(metric: DropwizardTimer) extends Timer(metric) {

  def timePartialFunction[A, B](pf: PartialFunction[A, B]): PartialFunction[A, B] = {
    new PartialFunction[A, B] {
      override def isDefinedAt(x: A): Boolean = pf.isDefinedAt(x)

      override def apply(v1: A): B =
        time {
          pf.apply(v1)
        }
    }
  }
}
