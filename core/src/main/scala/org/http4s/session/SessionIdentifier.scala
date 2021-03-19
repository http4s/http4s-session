package org.http4s.session

import io.chrisdavenport.random._
import cats._
import cats.syntax.all._
import org.http4s._

case class SessionIdentifier(value: String)
object SessionIdentifier {
  import java.util.Base64
  private val b64 = Base64.getEncoder()

  /**
    * Generator for a SecureIdentifier
    *
    * @param random Use Something backed by SecureRandom or a CSPRNG source
    * @param numBytes Minimum recommended by OWASP is 16 bytes if you have 64 bits of entropy
    * @return A Session Identifier
    */
  def create[F[_]: Functor](random: Random[F], numBytes: Int): F[SessionIdentifier] = {
    random.nextBytes(numBytes).map(a => SessionIdentifier(b64.encodeToString(a)))
  }

  /**
    * Convenience Method From Extracting a Session from a Request
    *
    * @param req Request to extract from
    * @param sessionIdentifierName The Name Of The Session Identifier - A.k.a Which cookie its in
    */
  def extract[F[_]](req: Request[F], sessionIdentifierName: String): Option[SessionIdentifier] = {
    req.cookies.find(_.name === sessionIdentifierName).map(c => SessionIdentifier(c.content))
  }
}