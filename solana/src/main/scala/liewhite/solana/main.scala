package liewhite.solana
import liewhite.json.{*, given}
import org.p2p.solanaj.rpc.RpcClient
import org.p2p.solanaj.rpc.Cluster
import org.p2p.solanaj.token.TokenManager
import org.p2p.solanaj.core.Account
import org.bitcoinj.core.Base58
import org.p2p.solanaj.core.PublicKey
import java.util.Base64

@main def main = {
  val client = new RpcClient(Cluster.MAINNET);
  val token  = TokenManager(client)
  val signer = Account(
    Base58.decode("")
  )
  // PublicKey.findProgramAddress()
  val usdc   = PublicKey("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
  val token_addr = client.getApi().getTokenAccountsByOwner(signer.getPublicKey(), usdc)
  val result = token.transfer(signer, token_addr, token_addr, usdc, 1)
  println(result)
}
