package io.chrisdavenport.http4ssession

import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.implicits._
import io.chrisdavenport.http4ssession.syntax.all._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder

object PersonalViewCounter extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    server[IO].use(_ => IO.never).as(ExitCode.Success)
  }

  def server[F[_]: Concurrent: Timer: ContextShift]: Resource[F, Unit] = {
    for {
      store <- Resource.liftF(SessionStore.create[F, PageViews]())
      routes = SessionMiddleware.optional(store, secure = false)(app)

      _ <- EmberServerBuilder.default[F]
        .withHttpApp(routes.orNotFound)
        .build

    } yield ()
  }

  case class PageViews(int: Int)

  def app[F[_]: Defer: Applicative]: SessionApp[F, Option[PageViews]] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    SessionApp.ofOptional[F][PageViews]{
      case GET -> Root / "reset" as _ => 
        Ok("Reset PageViews").withContext(None)
      case GET -> _ as Some(views) => 
        Ok(s"You've Been Here ${views.int}")
          .withContext(views.copy(views.int + 1).some)
      case GET -> _ as None =>  Ok("You've Never Been Here Before").withContext(PageViews(1).some)
    }
  }

}