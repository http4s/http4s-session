package org.http4s.session

import cats._
import cats.syntax.all._
import cats.data._
import org.http4s._
import org.http4s.headers.`Set-Cookie`
import org.typelevel.vault.Vault
import org.typelevel.vault.Key

object VaultSessionMiddleware {
  case object VaultSessionReset {
    val key = Key.newKey[cats.effect.SyncIO, VaultSessionReset.type].unsafeRunSync
  }
  case class VaultKeysToRemove(l: List[Key[_]])
  object VaultKeysToRemove {
    val key = Key.newKey[cats.effect.SyncIO, VaultKeysToRemove].unsafeRunSync
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
      val initVault = contextRequest.context.fold(contextRequest.req.attributes)(context =>
        Vault.union(context, contextRequest.req.attributes)
      )
      routes
        .run(contextRequest.req.withAttributes(initVault))
        .map { resp =>
          val outContext =
            contextRequest.context.fold(resp.attributes)(context => Vault.union(context, resp.attributes))
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
            )(reset => ContextResponse(None, resp.withAttributes(outContext)))
        }
    }
  }
}
