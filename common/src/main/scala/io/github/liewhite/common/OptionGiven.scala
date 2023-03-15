package io.github.liewhite.common

import scala.util.NotGiven

trait OptionGiven[T]:
  def give: Option[T]

object OptionGiven:
  given exist[T](using NotGiven[T]): OptionGiven[T] with
    def give = None

  given notExist[T](using t: T): OptionGiven[T] with
    def give = Some(t)
