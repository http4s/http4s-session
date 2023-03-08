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

import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.implicits._
import org.http4s.session._
import org.http4s.session.syntax.all._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder

object PersonalViewCounter extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    server[IO].use(_ => IO.never).as(ExitCode.Success)
  }

  def server[F[_]: Concurrent: Timer: ContextShift]: Resource[F, Unit] = {
    for {
      store <- Resource.eval(SessionStore.create[F, PageViews]())
      routes = SessionMiddleware.optional(store, secure = false)(app)

      _ <- EmberServerBuilder
        .default[F]
        .withHttpApp(routes.orNotFound)
        .build
    } yield ()
  }

  case class PageViews(int: Int)

  def app[F[_]: Monad]: SessionRoutes[F, Option[PageViews]] = {
    val dsl = new Http4sDsl[F] {}; import dsl._
    SessionRoutes.of {
      case GET -> Root / "reset" as _ =>
        Ok("Reset PageViews").withContext(None)
      case GET -> Root / "passthrough" as initial =>
        Ok("Hit Passthrough").withContext(initial)
      case GET -> _ as Some(views) =>
        Ok(s"You've been here ${views.int} time")
          .withContext(views.copy(views.int + 1).some)
      case GET -> _ as None => Ok("You've never been here before").withContext(PageViews(1).some)
    }
  }

}
