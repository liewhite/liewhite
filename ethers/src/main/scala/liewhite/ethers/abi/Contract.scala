package liewhite.ethers.abi

import scala.quoted.*
import liewhite.json.{*,given}

class Functions(functions: Map[String, ABIFunction]) extends Selectable {
  def selectDynamic(name: String): Any =
    "asd"
}

class Events extends Selectable {
  def selectDynamic(name: String): Any =
    "asd"
}

class Contract[FS <: Functions, ES <: Events](val functions: FS, val events: ES)

object Contract {
  transparent inline def apply(inline abiDef: String) =
    ${ impl('abiDef) }

  private def impl(abiExpr: Expr[String])(using q: Quotes): Expr[Any] = {
    // 解析ABI， 生成 Functions和Events， 然后构建出Contract
    import q.reflect.*
    val abiValue = abiExpr.valueOrAbort
    val a        = abiValue.fromJson[Seq[ABIItem]].toOption.get
    val fs = a.filter(_.`type` == ABIItemType.function)
    val es = a.filter(_.`type` == ABIItemType.event)
    '{
        new Contract(${makeFs(fs)}, ${makeEs(es)})
    }
  }

  private def makeFs(fs: Seq[ABIItem])(using q: Quotes): Expr[Functions] = {
    ???
  }
  private def makeEs(es: Seq[ABIItem])(using q: Quotes): Expr[Events] = {
    ???
  }

}
