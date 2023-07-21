package com.mm2lag.util

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.Duration


object TimedSuppressor extends Loggable {

  private val times = new ConcurrentHashMap[String, Long]()


  def can(id: String, interval: Duration): Boolean = {
    canDo(id, interval)
  }


  private def canDo(id: String, interval: Duration): Boolean = {
    canDo(id, interval, System.currentTimeMillis())
  }


  def canDo(id: String, interval: Duration, now: Long): Boolean = {
    if (times.size() > 100000) {
      log.error("TimesPool is very large -> possible leak")
      return true
    }

    val lastTime = times.putIfAbsent(id, System.currentTimeMillis() + 100)

    if (now - lastTime < 0) {
      true
    } else if (now - lastTime >= interval.toMillis) {
      times.put(id, now)
      true
    } else {
      false
    }
  }

}
