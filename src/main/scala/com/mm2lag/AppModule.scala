package com.mm2lag

import akka.actor.ActorSystem
import com.google.inject.{AbstractModule, Singleton}
import com.mm2lag.config.AppConf
import com.mm2lag.controller.OffsetsController
import com.mm2lag.service.{ClustersService, HttpServer, OffsetsStore}
import com.mm2lag.util.Loggable
import net.codingwell.scalaguice.ScalaModule

class AppModule(conf: AppConf, actorSystem: ActorSystem)
  extends AbstractModule with ScalaModule with Loggable {

  override def configure(): Unit = {
    bind[ActorSystem].toInstance(actorSystem)
    bind[AppConf].toInstance(conf)
    bind[ClustersService].in[Singleton]()
    bind[MM2LagMeter].in[Singleton]()
    bind[OffsetsStore].in[Singleton]()
    bind[HttpServer].in[Singleton]()
    bind[OffsetsController].in[Singleton]()
  }

}
