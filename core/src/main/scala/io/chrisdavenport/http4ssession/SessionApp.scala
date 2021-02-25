package io.chrisdavenport.http4ssession

import cats._
import cats.data._
import org.http4s._
import cats.syntax.all._

object SessionApp {
  def ofOptional[F[_]] = new SessionAppOptionalPartiallyApplied[F] 
  class SessionAppOptionalPartiallyApplied[F[_]]{
    def apply[A](pf: PartialFunction[ContextRequest[F, Option[A]], F[ContextResponse[F, Option[A]]]])(implicit ev1: Defer[F], ev2: Applicative[F]): SessionApp[F, Option[A]] = {
      Kleisli(req => OptionT(Defer[F].defer(pf.lift(req).sequence)))
    }
  }

  def of[F[_]] = new SessionAppPartiallyApplied[F]

  class SessionAppPartiallyApplied[F[_]]{
    def apply[A](pf: PartialFunction[ContextRequest[F, A], F[ContextResponse[F, A]]])(implicit ev1: Defer[F], ev2: Applicative[F]): SessionApp[F, A] = {
      Kleisli(req => OptionT(Defer[F].defer(pf.lift(req).sequence)))
    }
  }

  // def lift
}