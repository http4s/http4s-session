package org.http4s.session

import cats._
import cats.syntax.all._
import org.http4s._

object syntax {
  trait all {
    implicit class WithContext[F[_]](private val resp: Response[F]) {
      def withContext[A](a: A): ContextResponse[F, A] = ContextResponse(a, resp)
    }
    implicit class WithContextF[F[_]](private val f: F[Response[F]]) {
      def withContext[A](a: A)(implicit F: Functor[F]): F[ContextResponse[F, A]] =
        f.map(resp => ContextResponse(a, resp))
    }

  }
  object all extends all
}
