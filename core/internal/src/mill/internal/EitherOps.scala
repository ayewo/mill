package mill.internal

import scala.collection.{BuildFrom, mutable}

private[mill] object EitherOps {

  // implementation similar to scala.concurrent.Future#sequence
  def sequence[A, B, M[X] <: IterableOnce[X]](in: M[Either[A, B]])(
      implicit cbf: BuildFrom[M[Either[A, B]], B, M[B]]
  ): Either[A, M[B]] = {
    in.iterator
      .foldLeft[Either[A, mutable.Builder[B, M[B]]]](Right(cbf.newBuilder(in))) {
        case (acc, el) =>
          for (a <- acc; e <- el) yield a += e
      }
      .map(_.result())
  }
}
