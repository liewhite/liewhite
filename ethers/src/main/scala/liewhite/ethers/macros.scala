package liewhite.ethers

import scala.quoted.*
import liewhite.json.{*, given}
import liewhite.ethers.abi.ABIItem
import liewhite.ethers.abi.ABIItemType

inline def showTree[A](inline a: A): String = ${showTreeImpl[A]('{ a })}

def showTreeImpl[A: Type](a: Expr[A])(using Quotes): Expr[String] =
  import quotes.reflect.*
  Expr(a.asTerm.show)

transparent inline def contractFromABI(inline abi: String) =
  ${ contractFromABIImpl('abi) }

private def contractFromABIImpl(abi: Expr[String])(using q: Quotes): Expr[Any] = {
  import q.reflect.*
  val items = abi.valueOrAbort.fromJson[Seq[ABIItem]]
  val tp = Class.forName("")
  // val functions: Expr[Any]
  ???

}

private def makeFunction(item: ABIItem)(using q: Quotes) = {
  import q.reflect.*
  val name = item.name.get
  ???
}

// 从abi生成encoder， encoder.encode 方法的签名需要在macro里手动构建