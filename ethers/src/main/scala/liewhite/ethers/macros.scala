package liewhite.ethers

import scala.quoted.*
import liewhite.json.{*, given}

transparent inline def encoderFromABI(inline abi: String) =
  ${ encoderFromABIImpl('abi) }

private def encoderFromABIImpl(abi: Expr[String])(using q: Quotes): Expr[Any] = {
  import q.reflect.*
  val tp = TypeRepr.typeConstructorOf(ClassLoader.getSystemClassLoader().loadClass(""))
  tp.asType match
    case '[t] => {
      '{
        ???
      }
    }

}
