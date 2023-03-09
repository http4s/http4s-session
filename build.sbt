val Scala213 = "2.13.10"

ThisBuild / tlBaseVersion := "0.1"
ThisBuild / crossScalaVersions := Seq(Scala213)
ThisBuild / scalaVersion := crossScalaVersions.value.last
ThisBuild / developers := List(
  Developer("ChristopherDavenport",
            "Christopher Davenport",
            "chris@christopherdavenport.tech",
            url("https://github.com/ChristopherDavenport")
  )
)
ThisBuild / tlSitePublishBranch := Some("main")
ThisBuild / homepage := Some(url("https://github.com/http4s/http4s-session"))
ThisBuild / licenses := List("MIT" -> url("http://opensource.org/licenses/MIT"))

val Scala213Cond = s"matrix.scala == '$Scala213'"

val catsV = "2.9.0"
val catsEffectV = "2.5.5"
val fs2V = "2.5.11"
val http4sV = "0.22.15"
val circeV = "0.13.0"
val doobieV = "0.9.4"
val log4catsV = "1.1.1"
val munitCatsEffectV = "1.0.7"
val kindProjectorV = "0.13.2"
val betterMonadicForV = "0.3.1"

// Projects
lazy val `http4s-session` = project
  .in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, examples)

lazy val core = project
  .in(file("core"))
  .settings(
    name := "http4s-session",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsV,
      "org.typelevel" %% "cats-effect" % catsEffectV,
      "io.chrisdavenport" %% "random" % "0.0.2",
      "io.chrisdavenport" %% "mapref" % "0.1.1",
      "org.http4s" %% "http4s-core" % http4sV,
      "org.typelevel" %%% "munit-cats-effect-2" % munitCatsEffectV % Test
    )
  )

lazy val examples = project
  .in(file("examples"))
  .enablePlugins(NoPublishPlugin)
  .dependsOn(core)
  .settings(
    name := "http4s-session-examples",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sV,
      "org.http4s" %% "http4s-ember-server" % http4sV,
      "org.typelevel" %%% "munit-cats-effect-2" % munitCatsEffectV % Test
    )
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(core)
  .enablePlugins(Http4sOrgSitePlugin)
