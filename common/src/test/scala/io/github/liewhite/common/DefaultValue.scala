package io.github.liewhite.common

case class A(a: Int, b: String ="123")
class DefaultValueTest extends munit.FunSuite {
  test("hello") {
    val values = summon[DefaultValue[A]].defaults
    println(values)

    assertEquals(values, Map("b" -> "123"))
  }
}

