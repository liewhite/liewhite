package liewhite.ethers.abi.types

import org.web3j.abi.datatypes.StaticStruct
import org.web3j.abi.datatypes.Type
import scala.jdk.CollectionConverters.*
import org.web3j.abi.datatypes.StaticArray
import java.{util => ju}
import scala.jdk.CollectionConverters.*

class ABIStaticArray[T <: Type[_]](val elem: Seq[T]) extends StaticArray[T](elem.asJava) {}

object ABIStaticArray {
  given a[T <: Type[_]]: Conversion[ABIStaticArray[T], java.util.List[T]] with {
    def apply(a: ABIStaticArray[T]) =
      a.elem.asJava

  }
}
