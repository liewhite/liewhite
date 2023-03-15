package io.github.liewhite.sqlx

import scala.quoted.*
import scala.deriving.Mirror
import scala.compiletime.summonInline

object DSLMacros {
  transparent inline def refinementQuery[Q <: Query, T <: Product](
      q: Q,
      table: Table[T]
    ) = {
    ${ refinementQueryImpl[Q, T]('q, 'table) }
  }

  def refinementQueryImpl[Q <: Query: Type, T <: Product: Type](
      q: Expr[Q],
      table: Expr[Table[T]]
    )(using Quotes
    ): Expr[Any] = {
    import quotes.reflect.*

    val tableName     = TypeRepr.of[T].classSymbol.get.name
    val tableNameExpr = Expr(tableName)

    def recur[mels: Type, mets: Type](baseType: TypeRepr): TypeRepr = {
      Type.of[mels] match
        case '[mel *: melTail] => {
          Type.of[mets] match {
            case '[head *: tail] => {
              val label     = Type.valueOfConstant[mel].get.toString
              val withField =
                Refinement(baseType, label, TypeRepr.of[Field[head]])
              recur[melTail, tail](withField)
            }
          }
        }
        case '[EmptyTuple]     => baseType
    }

    Expr.summon[Mirror.ProductOf[T]].get match {
      case '{
            $m: Mirror.ProductOf[T] {
              type MirroredElemLabels = mels; type MirroredElemTypes = mets
            }
          } =>
        val tableType = recur[mels, mets](TypeRepr.of[Table[T]])

        tableType.asType match {
          // tType is refined type of table
          case '[tType] => {
            Refinement(
              TypeRepr.of[Q],
              tableName,
              TypeRepr.of[tType]
            ).asType match {
              // tpe is refined type of query
              case '[tpe] => {
                val res = '{
                  val newQ = new Query(${
                    q
                  }.tables.updated($tableNameExpr, $table))
                  newQ.asInstanceOf[tpe]
                }
                res
              }
            }
          }
        }
      case e => report.error(e.show); ???
    }

  }
}
