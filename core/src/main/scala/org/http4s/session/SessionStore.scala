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

import org.typelevel.vault._
import io.chrisdavenport.random._
import io.chrisdavenport.mapref._
import io.chrisdavenport.mapref.implicits._
import cats._
import cats.syntax.all._
import cats.effect._

trait SessionStore[F[_], A] {

  def createSessionId: F[SessionIdentifier]

  def getSession(id: SessionIdentifier): F[Option[A]]

  def modifySession[B](id: SessionIdentifier, f: Option[A] => (Option[A], B)): F[B]
}
object SessionStore {

  def create[F[_]: Sync, A](
    numShards: Int = 4,
    numBytes: Int = 32 // The session ID length must be at least 128 bits (16 bytes)
    // numBytes is entropy / 2 SecureRandom
  ): F[SessionStore[F, A]] = for {
    random <- Random.javaSecuritySecureRandom(numShards)
    ref <- MapRef.ofShardedImmutableMap[F, SessionIdentifier, A](numShards)
  } yield new MemorySessionStore[F, A](random, numBytes, ref)

  private class MemorySessionStore[F[_]: Functor, A](
    random: Random[F],
    numBytes: Int,
    access: MapRef[F, SessionIdentifier, Option[A]]
  ) extends SessionStore[F, A] {

    def createSessionId: F[SessionIdentifier] = SessionIdentifier.create(random, numBytes)

    def getSession(id: SessionIdentifier): F[Option[A]] = access(id).get

    def modifySession[B](id: SessionIdentifier, f: Option[A] => (Option[A], B)): F[B] =
      access(id).modify(f)
  }

}
