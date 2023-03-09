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

package examples

import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.session._
import org.typelevel.vault._

import VaultSessionMiddleware.VaultSessionReset

object VaultSessionExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    server[IO].use(_ => IO.never).as(ExitCode.Success)
  }

  def server[F[_]: Concurrent: Timer: ContextShift]: Resource[F, Unit] = {
    for {
      store <- Resource.eval(SessionStore.create[F, Vault]())
      key <- Resource.eval(Key.newKey[F, PageViews])
      routes = VaultSessionMiddleware.impl(store, secure = false)(app[F](key))

      _ <- EmberServerBuilder
        .default[F]
        .withHttpApp(routes.orNotFound)
        .build
    } yield ()
  }

  final case class PageViews(int: Int)

  def app[F[_]: Sync](key: Key[PageViews]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}; import dsl._
    HttpRoutes.of {
      case GET -> Root / "reset" =>
        Ok("Reset PageViews")
          .map(_.withAttribute(VaultSessionReset.key, VaultSessionReset))
      case req @ GET -> _ =>
        req.attributes
          .lookup(key)
          .fold(
            Ok("You've never been here before").map(_.withAttribute(key, PageViews(1)))
          )(pageViews =>
            Ok(s"You've been here ${pageViews.int} time").map(_.withAttribute(key, PageViews(pageViews.int + 1)))
          )
    }
  }

}
