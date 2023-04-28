val Scala213 = "2.13.10"
val Scala3 = "3.2.2"

ThisBuild / tlBaseVersion := "0.1"
ThisBuild / crossScalaVersions := Seq(Scala213, Scala3)
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
val catsEffectV = "3.4.10"
val fs2V = "3.6.1"
val http4sV = "0.23.18"
val munitCatsEffectV = "2.0.0-M3"

// Projects
lazy val `http4s-session` =
  tlCrossRootProject
    .aggregate(core)
    .enablePlugins(NoPublishPlugin)

lazy val core =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .in(file("core"))
    .settings(
      name := "http4s-session",
      libraryDependencies ++= Seq(
        "org.typelevel" %% "cats-core" % catsV,
        "org.typelevel" %% "cats-effect" % catsEffectV,
        "org.http4s" %% "http4s-core" % http4sV,
        "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectV % Test
      ),
      mimaPreviousArtifacts ~= { _.filterNot(_.revision == "0.1.0") }
    )

lazy val examples = project
  .in(file("examples"))
  .enablePlugins(NoPublishPlugin)
  .dependsOn(`http4s-session`.jvm)
  .settings(
    name := "http4s-session-examples",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sV,
      "org.http4s" %% "http4s-ember-server" % http4sV,
      "org.typelevel" %%% "munit-cats-effect" % munitCatsEffectV % Test
    )
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(`http4s-session`.jvm)
  .enablePlugins(Http4sOrgSitePlugin)
