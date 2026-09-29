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

import cats.data._
import cats.effect._
import munit.CatsEffectSuite
import org.http4s._
import org.http4s.implicits._
import org.typelevel.vault.Key

class VaultSessionMiddlewareSpec extends CatsEffectSuite {

  test("key can be set again after VaultKeysToRemove") {
    val k = Key.newKey[SyncIO, String].unsafeRunSync()

    val routes: HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
      OptionT.liftF(IO.pure(req.uri.path.renderString match {
        case "/set1"   => Response[IO]().withAttribute(k, "v1")
        case "/remove" =>
          Response[IO]().withAttribute(VaultSessionMiddleware.VaultKeysToRemove.key,
                                       VaultSessionMiddleware.VaultKeysToRemove(List(k))
          )
        case "/set2" => Response[IO]().withAttribute(k, "v2")
        case _       => Response[IO]().withEntity(req.attributes.lookup(k).toString)
      }))
    }

    for {
      store <- SessionStore.create[IO, org.typelevel.vault.Vault]()
      app = VaultSessionMiddleware.impl(store, secure = false)(routes).orNotFound
      r1 <- app.run(Request[IO](uri = uri"/set1"))
      cookie = r1.cookies.find(_.name == "id").get.content
      call = (u: Uri) => app.run(Request[IO](uri = u).addCookie("id", cookie))
      _ <- call(uri"/remove")
      removed <- call(uri"/get").flatMap(_.as[String])
      _ <- call(uri"/set2")
      body <- call(uri"/get").flatMap(_.as[String])
    } yield {
      assertEquals(removed, "None")
      assertEquals(body, "Some(v2)")
    }
  }
}
