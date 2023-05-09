package liewhite.common.zio

import zio.*

extension [E, A](effect: ZIO[Any, E, A]) {
  def force : A = {
    Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe
        .run(
          effect
        )
        .getOrThrowFiberFailure()
    }
  }
  def !! : A = force
}
