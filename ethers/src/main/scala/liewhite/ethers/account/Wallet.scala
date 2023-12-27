package liewhite.ethers.account

import org.web3j.crypto.MnemonicUtils
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Keys
import liewhite.ethers.*

class Wallet(val masterKeyPair: Bip32ECKeyPair) extends IndexedSeq[Account] {
  override def apply(i: Int): Account = {
    val bip32Keypair  = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, path(i))
    Account(bip32Keypair)
  }

  override def length: Int = Int.MaxValue

  val basePath = Array(44 | 0x80000000, 60 | 0x80000000, 0 | 0x80000000, 0)
  def path(index: Int): Array[Int] = {
    basePath.appended(index)
  }
}

object Wallet {
  def apply(mnemonic: String, pass: String = ""): Wallet = {
    val seed          = MnemonicUtils.generateSeed(mnemonic, pass)
    val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
    val path          = Array(44 | 0x80000000, 60 | 0x80000000, 0 | 0x80000000, 0, 0)
    new Wallet(masterKeyPair)
  }
}