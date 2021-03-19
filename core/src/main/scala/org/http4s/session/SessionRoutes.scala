package org.http4s.session

import cats._
import cats.data._
import org.http4s._
import cats.syntax.all._

object SessionRoutes {

  def of[F[_]] = new SessionRoutesOfPartiallyApplied[F]

  class SessionRoutesOfPartiallyApplied[F[_]]{
    def apply[A](pf: PartialFunction[ContextRequest[F, A], F[ContextResponse[F, A]]])(implicit ev1: Monad[F]): SessionRoutes[F, A] = {
      Kleisli(req => OptionT(Applicative[F].unit >> pf.lift(req).sequence))
    }
  }

}