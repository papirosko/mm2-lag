package com.mm2lag.util

import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration.{Duration, DurationInt}


/** Created by penkov on 05.05.16.
  */
trait Loggable {

  val log: Logger = LoggerFactory.getLogger(getClass)

  def suppressed(id: String, duration: Duration = 5.seconds)(body: => Any): Unit = {
    if (TimedSuppressor.can(id, duration)) {
      body
    }
  }

}
