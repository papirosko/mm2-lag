package com.mm2lag.util

import java.util.regex.Pattern

class PatternsMatcher(patterns: Iterable[String]) {

  private val regexes = patterns.map(p => Pattern.compile(p))

  def matches(name: String): Boolean = {
    regexes.exists(r => r.matcher(name).matches())
  }

}
