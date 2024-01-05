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
import cats.effect.std.Random
import cats.syntax.all._
import org.http4s._

final case class SessionIdentifier(value: String)
object SessionIdentifier {
  import java.util.Base64
  private val b64 = Base64.getEncoder()

  /**
   * Generator for a SecureIdentifier
   *
   * @param random
   *   Use Something backed by SecureRandom or a CSPRNG source
   * @param numBytes
   *   Minimum recommended by OWASP is 16 bytes if you have 64 bits of entropy
   * @return
   *   A Session Identifier
   */
  def create[F[_]: Functor](random: Random[F], numBytes: Int): F[SessionIdentifier] = {
    random.nextBytes(numBytes).map(a => SessionIdentifier(b64.encodeToString(a)))
  }

  /**
   * Convenience Method From Extracting a Session from a Request
   *
   * @param req
   *   Request to extract from
   * @param sessionIdentifierName
   *   The Name Of The Session Identifier - A.k.a Which cookie its in
   */
  def extract[F[_]](req: Request[F], sessionIdentifierName: String): Option[SessionIdentifier] = {
    req.cookies.find(_.name === sessionIdentifierName).map(c => SessionIdentifier(c.content))
  }
}
