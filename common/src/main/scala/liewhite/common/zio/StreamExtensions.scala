package liewhite.common.zio

import zio.stream.*
import scala.quoted.*
import zio.Chunk

object StreamExtensions {
  transparent inline def merge(inline streams: ZStream[_, _, _]*): Any =
    ${ mergeImpl('streams) }

  transparent inline def mergeSorted(
    inline streams: ZStream[_, _, (Long, _)]*
  ): Any =
    ${ mergeSortedImpl('streams) }

  private def mergeImpl(
    expr: Expr[Seq[ZStream[_, _, _]]]
  )(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    (rType(expr).asType, aType(expr).asType) match {
      case ('[r], '[a]) => {
        val result = expr match {
          case Varargs(streams) => {
            streams.tail.foldLeft(streams.head) { (acc, item) =>
              '{
                ${ acc }.merge(${ item })
              }
            }
          }
        }
        '{ ${ result }.asInstanceOf[ZStream[r, Throwable, a]] }
      }
    }
  }

  private def mergeSortedImpl(
    expr: Expr[Seq[ZStream[_, _, (Long, _)]]]
  )(using Quotes): Expr[Any] = {
    import quotes.reflect.*

    (rType(expr).asType, aTypeWithKey(expr).asType) match {
      case ('[r], '[a]) => {
        val result = expr match {
          case Varargs(streams) => {
            streams.tail.foldLeft(streams.head) { (acc, item) =>
              '{
                ${ acc }
                  .zipAllSortedByKeyWith(
                    ${ item }
                  )(Chunk(_), Chunk(_))((a, b) => Chunk(a, b))
                  .map(item => item._2.map(i => item._1 -> i))
                  .flattenChunks
              }
            }
          }
        }
        '{
          ${ result }
            .asInstanceOf[ZStream[r, Throwable, a]]
        }
      }
    }
  }

  private def rType(streams: Expr[Seq[ZStream[_, _, _]]])(using Quotes) = {
    import quotes.reflect.*
    streams match
      case Varargs(streams) => {
        streams.foldLeft(TypeRepr.of[Any]) { (acc, item) =>
          acc.asType match {
            case '[f] => {
              item.asTerm.tpe.asType match
                case '[ZStream[r, e, a]] => TypeRepr.of[f & r]
            }
          }
        }
      }
  }

  private def aType(streams: Expr[Seq[ZStream[_, _, _]]])(using Quotes) = {
    import quotes.reflect.*
    streams match
      case Varargs(streams) => {
        streams.head.asTerm.tpe.asType match {
          case '[ZStream[r2, e2, a2]] => {
            streams.tail.foldLeft(TypeRepr.of[a2]) { (acc, item) =>
              acc.asType match {
                case '[f] => {
                  item.asTerm.tpe.asType match
                    case '[ZStream[r, e, a]] => TypeRepr.of[f | a]
                }
              }
            }
          }
        }
      }
  }

  private def aTypeWithKey(
    streams: Expr[Seq[ZStream[_, _, (Long, _)]]]
  )(using Quotes) = {
    import quotes.reflect.*
    val valueType = streams match
      case Varargs(streams) => {
        streams.head.asTerm.tpe.asType match {
          case '[ZStream[r2, e2, (Long, a2)]] => {
            streams.tail.foldLeft(TypeRepr.of[a2]) { (acc, item) =>
              acc.asType match {
                case '[f] => {
                  item.asTerm.tpe.asType match
                    case '[ZStream[r, e, (Long, a)]] => TypeRepr.of[f | a]
                }
              }
            }
          }
        }
      }
    valueType.asType match {
      case '[a] => TypeRepr.of[(Long, a)]
    }
  }
}
