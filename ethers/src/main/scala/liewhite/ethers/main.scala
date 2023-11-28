// package liewhite.ethers
// import zio.*

// import liewhite.common.zio.{*, given}
// import org.web3j.abi.TypeEncoder
// import org.web3j.abi.datatypes
// import java.math.BigInteger
// import liewhite.json.{*, given}
// import liewhite.ethers.abi.ABIItem
// import liewhite.ethers.abi.Contract
// import java.nio.ByteBuffer
// import org.web3j.abi.datatypes.*
// import liewhite.ethers.abi.types.*
// import org.web3j.abi.datatypes.generated.Uint8
// import org.web3j.abi.datatypes.generated.Uint256

// @main def main = {
//   val c = Contract(abiStr)
//   val f = c.functions.addStrategiesToDepositWhitelist
//   println(f.encode(Tuple(ABIStaticArray(Seq(Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31"))))).toHex)
//   val p2 = (
//       Seq(
//         Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31")
//       ),
//       Seq(BigInt(1)),
//       Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31"),
//       (Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31"), BigInt(1)),
//       BigInt(1),
//       Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31")
//     )
//   val f2 = c.functions.slashQueuedWithdrawal.encode((
//     Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31"),
//     p2,
//     Seq(Address("0x10F13d9598De04850F59fD2E82837E1c17aD7E31")),
//     Seq(BigInt(1))
//   ))
//   println(f2.toHex)
// }
