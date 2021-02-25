package io.chrisdavenport

import cats.data._
import org.http4s._

package object http4ssession {
  type SessionRoutes[F[_], A] = Kleisli[OptionT[F, *], ContextRequest[F, A], ContextResponse[F, A]]
}
