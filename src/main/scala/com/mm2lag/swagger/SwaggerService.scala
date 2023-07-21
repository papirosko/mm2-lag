package com.mm2lag.swagger

import com.github.swagger.akka.model.Info
import com.mm2lag.controller.{MetricsController, OffsetsController}

object SwaggerDocService extends SwaggerHttpWithUiService {
  override val apiClasses = Set(
    classOf[OffsetsController],
    MetricsController.getClass
  )
  override val serverURLs = Seq("")
  override val info: Info = Info(title = "MM2 Lag", version = "1.0")
}
