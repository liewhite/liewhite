package io.github.liewhite.common

import scala.annotation.StaticAnnotation

case class Ann1(arg: Int) extends StaticAnnotation

@Ann1(1)
case class Annotatee(
    @Ann1(1)
    a: Int,
    b: String = "123"
)

class AnnotationTest extends munit.FunSuite {
  test("class annotation") {
    val values = summon[RepeatableAnnotation[Ann1, Annotatee]]()
    println(values)

    assertEquals(values, List(Ann1(1)))
  }

  test("field annotations") {
    val values = summon[RepeatableAnnotations[Ann1, Annotatee]]()
    println(values)

    assertEquals(values, List(List(Ann1(1)), List.empty))
  }
}
