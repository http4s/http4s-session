/*
 * Copyright (c) 2023 http4s.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.http4s.session

import cats._
import cats.data._
import cats.syntax.all._
import org.http4s._
import org.typelevel.vault.Key
import org.typelevel.vault.Vault

object VaultSessionMiddleware {
  case object VaultSessionReset {
    val key = Key.newKey[cats.effect.SyncIO, VaultSessionReset.type].unsafeRunSync()
  }

  final case class VaultKeysToRemove(l: List[Key[_]])
  object VaultKeysToRemove {
    val key = Key.newKey[cats.effect.SyncIO, VaultKeysToRemove].unsafeRunSync()
  }

  def impl[F[_]: Monad, A](
    sessionStore: SessionStore[F, Vault],
    sessionIdentifierName: String = "id",
    httpOnly: Boolean = true,
    secure: Boolean = true,
    domain: Option[String] = Option.empty[String],
    path: Option[String] = None,
    sameSite: SameSite = SameSite.Lax,
    expiration: SessionMiddleware.ExpirationManagement[F] = SessionMiddleware.ExpirationManagement.Static[F](None, None)
  )(httpRoutes: HttpRoutes[F]): HttpRoutes[F] = {
    SessionMiddleware.optional(
      sessionStore,
      sessionIdentifierName,
      httpOnly,
      secure,
      domain,
      path,
      sameSite,
      expiration,
      None
    )(transformRoutes(httpRoutes))
  }

  def transformRoutes[F[_]: Functor](routes: HttpRoutes[F]): SessionRoutes[F, Option[Vault]] = {
    Kleisli { contextRequest: ContextRequest[F, Option[Vault]] =>
      val initVault =
        contextRequest.context.fold(contextRequest.req.attributes)(context => context ++ contextRequest.req.attributes)
      routes
        .run(contextRequest.req.withAttributes(initVault))
        .map { resp =>
          val outContext =
            contextRequest.context.fold(resp.attributes)(_ ++ resp.attributes)
          outContext
            .lookup(VaultSessionReset.key)
            .fold(
              outContext
                .lookup(VaultKeysToRemove.key)
                .fold(
                  ContextResponse(outContext.some, resp.withAttributes(outContext))
                )(toRemove =>
                  ContextResponse(toRemove.l.foldLeft(outContext) { case (v, k) => v.delete(k) }.some,
                                  resp.withAttributes(outContext)
                  )
                )
            )(_ => ContextResponse(None, resp.withAttributes(outContext)))
        }
    }
  }
}
