package com.mm2lag.controller

import akka.http.scaladsl.model.{ContentTypes, HttpCharsets, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{Directives, Route}
import com.mm2lag.util.metrics.MetricsHandlerSupport
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.{GET, Path, Produces}

import java.io.Writer
import scala.collection.mutable


@Path("/")
@Tag(name = "metrics")
object MetricsController extends Directives with MetricsHandlerSupport {

  val routes: Route = metricsJson ~ metricsProm

  @GET()
  @Path("metrics")
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(summary = "metrics in json format")
  def metricsJson: Route = path("metrics") {
    get {
      complete(HttpEntity(ContentTypes.`application/json`, collectMetrics().spaces4))
    }
  }

  @GET()
  @Path("prometheusmetrics")
  @Produces(Array(MediaType.TEXT_PLAIN))
  @Operation(summary = "metrics in prometheus format")
  def metricsProm: Route = Directives.path("prometheusmetrics") {
    get {
      complete(HttpEntity(
        MediaTypes.`text/plain` withParams Map("version" -> "0.0.4") withCharset HttpCharsets.`UTF-8`,
        metricsPromDraw
      ))
    }
  }

  private def metricsPromDraw: String = {
    val buffer = new mutable.StringBuilder()
    val writer = new WriterAdapter(buffer)
    TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples())
    writer.close()
    buffer.toString()
  }
}

class WriterAdapter(buffer: mutable.StringBuilder) extends Writer {

  override def write(charArray: Array[Char], offset: Int, length: Int): Unit = {
    buffer ++= new String(new String(charArray, offset, length).getBytes("UTF-8"), "UTF-8")
  }

  override def flush(): Unit = {}

  override def close(): Unit = {}
}
