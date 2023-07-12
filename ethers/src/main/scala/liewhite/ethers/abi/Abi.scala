package liewhite.ethers.abi

import liewhite.json.{*, given}
import zio.schema

enum ABIItemType derives Schema {
  case function
  case constructor
  case receive
  case fallback
  case event
  case error
}
case class Input(
  name: String,
  `type`: String,
  components: Option[Seq[Input]],
  indexed: Option[Boolean]
) derives Schema

case class ABIItem(
  `type`: ABIItemType,
  name: Option[String], //Constructor, receive, and fallback never have name or outputs
  inputs: Seq[Input],
  outputs: Option[Seq[Input]],
  anonymous: Option[Boolean]
) derives Schema
