package io.chrisdavenport.http4ssession

import cats._ 
import cats.syntax.all._
import cats.data._
import org.http4s._

object SessionMiddleware {

  def apply[F[_]: Monad, A](
    sessionStore: SessionStore[F, A], 
    default: A,
    sessionIdentifierName: String = "id",
    httpOnly: Boolean = true,
    secure: Boolean = true,
    path: Option[String] = None,
    sameSite: SameSite = SameSite.Lax,
  )(sessionApp: SessionApp[F, A]): HttpRoutes[F] = 
    Kleisli{req: Request[F] => 
      val sessionId = SessionIdentifier.extract(req, sessionIdentifierName)
      val session = sessionId.flatTraverse(id => sessionStore.getSession(id))

      for {
        sessionOpt <- OptionT.liftF(session)
        response <- sessionApp(ContextRequest(sessionOpt.getOrElse(default), req))
        out <- OptionT.liftF(sessionId match {
          case Some(id) =>
            sessionStore.modifySession(id, {now => (response.context.some, ())})
              .as(response.response) // No Need to Modify Current Session
          case None => 
            sessionStore.createSessionId.flatMap(id => 
              sessionStore.modifySession(id, _ => (response.context.some, ())) >> 
                response.response.putHeaders(org.http4s.headers.`Set-Cookie`(
                  ResponseCookie(sessionIdentifierName, id.value, httpOnly = httpOnly, secure = secure, path = path, sameSite = sameSite)
                )).pure[F]
            )
        })
      } yield out
    }
  
  def optional[F[_]: Monad, A](
    sessionStore: SessionStore[F, A], 
    sessionIdentifierName: String = "id",
    httpOnly: Boolean = true,
    secure: Boolean = true,
    path: Option[String] = None,
    sameSite: SameSite = SameSite.Lax
  )(sessionApp: SessionApp[F, Option[A]]): HttpRoutes[F] = 
    Kleisli{req: Request[F] => 
      val sessionId = SessionIdentifier.extract(req, sessionIdentifierName)
      val session = sessionId.flatTraverse(id => sessionStore.getSession(id))

      for {
        sessionOpt <- OptionT.liftF(session)
        response <- sessionApp(ContextRequest(sessionOpt, req))
        out <- OptionT.liftF((sessionId, response.context) match {
          case (None, None) =>response.response .pure[F]
          case (Some(id), Some(context)) =>
            sessionStore.modifySession(id, {now => (context.some, ())})
              .as(response.response) // No Need to Modify Current Session
          case (None, Some(context)) => 
            sessionStore.createSessionId.flatMap(id => 
              sessionStore.modifySession(id, _ => (context.some, ())) >> 
                response.response.putHeaders(org.http4s.headers.`Set-Cookie`(
                  ResponseCookie(sessionIdentifierName, id.value, httpOnly = httpOnly, secure = secure, path = path, sameSite = sameSite)
                )).pure[F]
            )
          case (Some(id), None) => 
            sessionStore.modifySession(id, _ => (None, ())) >> 
              response.response.putHeaders(org.http4s.headers.`Set-Cookie`(
                ResponseCookie(sessionIdentifierName, "deleted", httpOnly = httpOnly, secure = secure, path = path, sameSite = sameSite, expires = Some(HttpDate.Epoch))
              )).pure[F]
          
        })
      } yield out
    }
}