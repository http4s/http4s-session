package io.chrisdavenport.http4ssession

import io.chrisdavenport.random._
import cats._
import cats.syntax.all._
import org.http4s._

case class SessionIdentifier(value: String)
object SessionIdentifier {
  import java.util.Base64
  private val b64 = Base64.getEncoder()
  // Use something backed by a SecureRandom or a CSPRNG Generator
  // 
  def create[F[_]: Functor](random: Random[F], numBytes: Int = 16): F[SessionIdentifier] = {
    random.nextBytes(numBytes).map(a => SessionIdentifier(b64.encodeToString(a)))
  }

  def extract[F[_]](req: Request[F], sessionIdentifierName: String): Option[SessionIdentifier] = {
    req.cookies.find(_.name === sessionIdentifierName).map(c => SessionIdentifier(c.content))
  }
}