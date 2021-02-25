package io.chrisdavenport.http4ssession

import munit.CatsEffectSuite
import cats.effect._

class MainSpec extends CatsEffectSuite {

  test("Main should exit succesfully") {
    assertEquals(1, 1)
  }

}
