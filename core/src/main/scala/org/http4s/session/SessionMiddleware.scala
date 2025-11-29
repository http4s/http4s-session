/*
 * Copyright (c) 2024 http4s.org
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
import org.http4s.headers.`Set-Cookie`

object SessionMiddleware {

  def optional[F[_]: Monad, A](
    sessionStore: SessionStore[F, A],
    sessionIdentifierName: String = "id",
    httpOnly: Boolean = true,
    secure: Boolean = true,
    domain: Option[String] = Option.empty[String],
    path: Option[String] = None,
    sameSite: SameSite = SameSite.Lax,
    expiration: ExpirationManagement[F] = ExpirationManagement.Static[F](None, None),
    mergeOnChanged: Option[MergeManagement[Option[A]]] =
      Option.empty[MergeManagement[Option[A]]] // Default Is to Override Uncoditionally with newer info
  )(sessionApp: SessionRoutes[F, Option[A]]): HttpRoutes[F] = {
    val deleteCookie: `Set-Cookie` = {
      `Set-Cookie`(
        ResponseCookie(
          sessionIdentifierName,
          "deleted",
          domain = domain,
          httpOnly = httpOnly,
          secure = secure,
          path = path,
          sameSite = sameSite.some,
          expires = Some(HttpDate.Epoch),
          maxAge = Some(-1L)
        )
      )
    }
    def sessionCookie(id: SessionIdentifier): F[`Set-Cookie`] = {
      expiration match {
        case ExpirationManagement.Static(maxAge, expires) =>
          `Set-Cookie`(
            ResponseCookie(sessionIdentifierName,
                           id.value,
                           domain = domain,
                           httpOnly = httpOnly,
                           secure = secure,
                           path = path,
                           sameSite = sameSite.some,
                           maxAge = maxAge,
                           expires = expires
            )
          ).pure[F]
        case e @ ExpirationManagement.Dynamic(fromNow) =>
          HttpDate.current[F](Functor[F], e.C).flatMap { now =>
            fromNow(now).map { case ExpirationManagement.Static(maxAge, expires) =>
              `Set-Cookie`(
                ResponseCookie(sessionIdentifierName,
                               id.value,
                               domain = domain,
                               httpOnly = httpOnly,
                               secure = secure,
                               path = path,
                               sameSite = sameSite.some,
                               maxAge = maxAge,
                               expires = expires
                )
              )
            }
          }
      }
    }
    Kleisli { (req: Request[F]) =>
      val sessionId = SessionIdentifier.extract(req, sessionIdentifierName)
      val session = sessionId.flatTraverse(id => sessionStore.getSession(id))

      for {
        sessionOpt <- OptionT.liftF(session)
        response <- sessionApp(ContextRequest(sessionOpt, req))
        out <- OptionT.liftF((sessionId, response.context) match {
          case (None, None)              => response.response.pure[F]
          case (Some(id), Some(context)) =>
            sessionStore.modifySession(
              id,
              { now =>
                val next: Option[A] = mergeOnChanged.fold(context.some) { mm =>
                  if (!mm.eqv(sessionOpt, now)) mm.whenDifferent(now, context.some)
                  else context.some
                }
                (next, ())
              }
            ) >>
              sessionCookie(id).map(response.response.putHeaders(_))
          case (None, Some(context)) =>
            sessionStore.createSessionId.flatMap(id =>
              sessionStore.modifySession(
                id,
                { now =>
                  val next: Option[A] = mergeOnChanged.fold(context.some) { mm =>
                    if (!mm.eqv(sessionOpt, now)) mm.whenDifferent(now, context.some)
                    else context.some
                  }
                  (next, ())
                }
              ) >> sessionCookie(id).map(response.response.putHeaders(_))
            )
          case (Some(id), None) =>
            sessionStore
              .modifySession(
                id,
                { now =>
                  val next: Option[A] = mergeOnChanged.fold(Option.empty[A]) { mm =>
                    if (!mm.eqv(sessionOpt, now)) mm.whenDifferent(now, Option.empty)
                    else None
                  }
                  (next, ())
                }
              )
              .as(response.response.putHeaders(deleteCookie))
        })
      } yield out
    }
  }

  def defaulted[F[_]: Monad, A](
    sessionStore: SessionStore[F, A],
    default: A,
    sessionIdentifierName: String = "id",
    httpOnly: Boolean = true,
    secure: Boolean = true,
    domain: Option[String] = Option.empty[String],
    path: Option[String] = None,
    sameSite: SameSite = SameSite.Lax,
    expiration: ExpirationManagement[F] = ExpirationManagement.Static[F](None, None),
    mergeOnChanged: Option[MergeManagement[Option[A]]] =
      Option.empty[MergeManagement[Option[A]]] // Default Is to Override Uncoditionally with newer info
  )(sessionApp: SessionRoutes[F, A]): HttpRoutes[F] =
    Kleisli { (req: Request[F]) =>
      val sessionId = SessionIdentifier.extract(req, sessionIdentifierName)
      val session = sessionId.flatTraverse(id => sessionStore.getSession(id))

      def sessionCookie(id: SessionIdentifier): F[`Set-Cookie`] = {
        expiration match {
          case ExpirationManagement.Static(maxAge, expires) =>
            `Set-Cookie`(
              ResponseCookie(sessionIdentifierName,
                             id.value,
                             domain = domain,
                             httpOnly = httpOnly,
                             secure = secure,
                             path = path,
                             sameSite = sameSite.some,
                             maxAge = maxAge,
                             expires = expires
              )
            ).pure[F]
          case e @ ExpirationManagement.Dynamic(fromNow) =>
            HttpDate.current[F](Functor[F], e.C).flatMap { now =>
              fromNow(now).map { case ExpirationManagement.Static(maxAge, expires) =>
                `Set-Cookie`(
                  ResponseCookie(sessionIdentifierName,
                                 id.value,
                                 domain = domain,
                                 httpOnly = httpOnly,
                                 secure = secure,
                                 path = path,
                                 sameSite = sameSite.some,
                                 maxAge = maxAge,
                                 expires = expires
                  )
                )
              }
            }
        }
      }

      for {
        sessionOpt <- OptionT.liftF(session)
        response <- sessionApp(ContextRequest(sessionOpt.getOrElse(default), req))
        out <- OptionT.liftF(sessionId match {
          case Some(id) =>
            sessionStore.modifySession(
              id,
              { now =>
                val next: Option[A] = mergeOnChanged.fold(Option.empty[A]) { mm =>
                  if (!mm.eqv(sessionOpt, now)) mm.whenDifferent(now, Option.empty)
                  else None
                }
                (next, ())
              }
            ) >> sessionCookie(id).map(response.response.putHeaders(_))
          case None =>
            sessionStore.createSessionId.flatMap(id =>
              sessionStore.modifySession(
                id,
                { now =>
                  val next: Option[A] = mergeOnChanged.fold(Option.empty[A]) { mm =>
                    if (!mm.eqv(sessionOpt, now)) mm.whenDifferent(now, Option.empty)
                    else None
                  }
                  (next, ())
                }
              ) >> sessionCookie(id).map(response.response.putHeaders(_))
            )
        })
      } yield out
    }

  /**
   * Http Servers allow concurrent requests. You may wish to specify how merges are managed if the context has been
   * concurrently modified while your service is holding some initial context.
   *
   * @param eqv
   *   Intended as equivalent to Eq.eqv in cats if present
   * @param whenDifferent
   *   How to resolve conflicts when a difference from the initial and present state has occured.
   */
  trait MergeManagement[A] {
    def eqv(a1: A, a2: A): Boolean
    def whenDifferent(changedValue: A, valueContextWishesToSet: A): A
  }
  object MergeManagement {
    def instance[A](areEqual: (A, A) => Boolean, conflictResolution: (A, A) => A): MergeManagement[A] =
      new MergeManagement[A] {
        def eqv(a1: A, a2: A): Boolean = areEqual(a1, a2)
        def whenDifferent(changedValue: A, valueContextWishesToSet: A): A =
          conflictResolution(changedValue, valueContextWishesToSet)
      }
  }

  /**
   * ExpirationManagement is how you can control the expiration of your Session Cookies. Static is fairly straight
   * forward. Static(None, None) means your session is ephemeral and will be removed when the browser closes
   *
   * Max Age Should Be Prefered in all cases as the Expires specification is only in terms of client anyway so static
   * MaxAge is effective, but may not be supported by all clients. If it is relative to the current time like MaxAge
   * will likely need to use Dynamic rather than a Static to render an Expires, and can be leveraged for that.
   */
  sealed trait ExpirationManagement[F[_]]
  object ExpirationManagement {
    final case class Static[F[_]](maxAge: Option[Long], expires: Option[HttpDate]) extends ExpirationManagement[F]
    final case class Dynamic[F[_]](fromNow: HttpDate => F[Static[F]])(implicit val C: cats.effect.Clock[F])
        extends ExpirationManagement[F]
  }

}
