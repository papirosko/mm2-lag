package com.mm2lag.util

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers

class PatternMatcherTests extends AnyFlatSpec with Matchers {


  "PatternsMatcher" must "match topic name" in {
    val m = new PatternsMatcher(Seq("users/.*"))
    m.matches("users/u1") mustBe true
    m.matches("u1") mustBe false
  }

  it must "not match if no patterns specified" in {
    val m = new PatternsMatcher(Nil)
    m.matches("users/u1") mustBe false
    m.matches("u1") mustBe false

  }

}
