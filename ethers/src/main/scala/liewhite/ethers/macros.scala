package liewhite.ethers

import scala.quoted.*

// data 直接就是abi type, 就像直接在solidity里一样
inline def abiencode(inline data: Any*): Array[Byte] =
  ${ abiencodeImpl('data) }


def abiencodeImpl(data: Expr[Seq[Any]])(using Quotes): Expr[Array[Byte]] = {
    import quotes.reflect.*
    // val value = data
    ???
}

// todo 生成abi type内部表示， 比如web3j的类型
def parseABIType[T:Type] = {

}
