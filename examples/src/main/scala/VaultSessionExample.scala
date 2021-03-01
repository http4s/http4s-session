package examples

import cats._
import cats.syntax.all._
import cats.effect._
import org.http4s._
import org.http4s.implicits._
import io.chrisdavenport.vault._
import io.chrisdavenport.http4ssession.syntax.all._
import io.chrisdavenport.http4ssession._
import VaultSessionMiddleware.VaultSessionReset
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.server.EmberServerBuilder

object VaultSessionExample extends IOApp {

  def run(args: List[String]): IO[ExitCode] = {
    server[IO].use(_ => IO.never).as(ExitCode.Success)
  }

  def server[F[_]: Concurrent: Timer: ContextShift]: Resource[F, Unit] = {
    for {
      store <- Resource.liftF(SessionStore.create[F, Vault]())
      key <- Resource.liftF(Key.newKey[F, PageViews])
      routes = VaultSessionMiddleware.impl(store, secure = false)(app[F](key))

      _ <- EmberServerBuilder.default[F]
        .withHttpApp(routes.orNotFound)
        .build
    } yield ()
  }

  case class PageViews(int: Int)

  def app[F[_]: Sync](key: Key[PageViews]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F]{}; import dsl._
    HttpRoutes.of{
      case GET -> Root / "reset" => 
        Ok("Reset PageViews")
          .map(_.withAttribute(VaultSessionReset.key, VaultSessionReset))
      case req@GET -> _  => 
        req.attributes.lookup(key).fold(
          Ok("You've never been here before").map(_.withAttribute(key, PageViews(1)))
        )(pageViews => 
          Ok(s"You've been here ${pageViews.int} time").map(_.withAttribute(key, PageViews(pageViews.int + 1)))
        )
    }
  }

}