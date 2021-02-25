package io.chrisdavenport.http4ssession

import io.chrisdavenport.vault._
import io.chrisdavenport.random._
import io.chrisdavenport.mapref._
import io.chrisdavenport.mapref.implicits._
import cats._
import cats.syntax.all._
import cats.effect._

trait SessionStore[F[_], A]{

  def createSessionId: F[SessionIdentifier]

  def getSession(id: SessionIdentifier): F[Option[A]]

  def modifySession[B](id: SessionIdentifier, f: Option[A] => (Option[A], B)): F[B]
}
object SessionStore {


  def create[F[_]: Sync, A](
    numShards: Int = 4,
    numBytes: Int = 32, // The session ID length must be at least 128 bits (16 bytes)
     // numBytes is entropy / 2 SecureRandom 
  ): F[SessionStore[F, A]] = for {
    random <- Random.javaSecuritySecureRandom(numShards)
    ref <- MapRef.ofShardedImmutableMap[F, SessionIdentifier, A](numShards)
  } yield new MemorySessionStore[F, A](random, numBytes, ref)


  private class MemorySessionStore[F[_]: Functor, A](
    random: Random[F],
    numBytes: Int,
    access: MapRef[F, SessionIdentifier, Option[A]]
  ) extends SessionStore[F, A]{

    def createSessionId: F[SessionIdentifier] = SessionIdentifier.create(random, numBytes)

    def getSession(id: SessionIdentifier): F[Option[A]] = access(id).get

    def modifySession[B](id: SessionIdentifier, f: Option[A] => (Option[A], B)): F[B] = 
      access(id).modify(f)
  }

}