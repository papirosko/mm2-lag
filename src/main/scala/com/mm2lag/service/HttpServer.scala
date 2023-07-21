package com.mm2lag.service

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives
import com.google.inject.Inject
import com.mm2lag.config.AppConf
import com.mm2lag.controller.{MetricsController, OffsetsController}
import com.mm2lag.swagger.SwaggerDocService
import com.mm2lag.swagger.SwaggerDocService.apiDocsPath
import com.mm2lag.util.Loggable

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class HttpServer @Inject()(conf: AppConf,
                           offsetsController: OffsetsController)
                          (private implicit val sys: ActorSystem)
  extends Loggable with Directives {

  private var binding: Http.ServerBinding = _

  private val route = concat(
    pathSingleSlash { // redirect to swagger by default
      redirect(s"/$apiDocsPath/", StatusCodes.Found)
    },
    SwaggerDocService.routes,
    offsetsController.route,
    MetricsController.routes,
  )


  def start(): Future[Http.ServerBinding] = {
    log.info(s"Starting http server on ${conf.http.host}:${conf.http.port}")
    Http().newServerAt(conf.http.host, conf.http.port).bind(route)
  }

  def stop(): Unit = {
    Option(binding).foreach {x =>
      Await.ready(x.unbind(), 5.seconds)
    }
  }


}
