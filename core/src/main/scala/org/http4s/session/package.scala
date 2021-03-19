package org.http4s

import cats.data._
import org.http4s._

package object session {
  type SessionRoutes[F[_], A] = Kleisli[OptionT[F, *], ContextRequest[F, A], ContextResponse[F, A]]
}
