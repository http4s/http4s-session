import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val Scala213 = "2.13.6"

ThisBuild / crossScalaVersions := Seq(Scala213)
ThisBuild / scalaVersion := crossScalaVersions.value.last

ThisBuild / githubWorkflowArtifactUpload := false

val Scala213Cond = s"matrix.scala == '$Scala213'"

def rubySetupSteps(cond: Option[String]) = Seq(
  WorkflowStep.Use("ruby",
                   "setup-ruby",
                   "v1",
                   name = Some("Setup Ruby"),
                   params = Map("ruby-version" -> "2.6.0"),
                   cond = cond
  ),
  WorkflowStep.Run(List("gem install saas", "gem install jekyll -v 3.2.1"),
                   name = Some("Install microsite dependencies"),
                   cond = cond
  )
)

ThisBuild / githubWorkflowBuildPreamble ++=
  rubySetupSteps(Some(Scala213Cond))

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep
    .Sbt(List("scalafmtCheckAll", "scalafmtSbtCheck"), name = Some("Check formatting")),
  WorkflowStep.Sbt(List("mimaReportBinaryIssues"), name = Some("Check binary issues")),
  WorkflowStep.Sbt(List("Test/compile"), name = Some("Compile")),
  WorkflowStep.Sbt(List("test"), name = Some("Run tests")),
  WorkflowStep.Sbt(List("site/makeMicrosite"), name = Some("Build the Microsite"), cond = Some(Scala213Cond))
)

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")

// currently only publishing tags
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")), RefPredicate.Equals(Ref.Branch("main")))

ThisBuild / githubWorkflowPublishPreamble ++=
  WorkflowStep.Use("olafurpg", "setup-gpg", "v3") +: rubySetupSteps(None)

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    name = Some("Publish artifacts to Sonatype"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  ),
  WorkflowStep.Use("christopherdavenport", "create-ghpages-ifnotexists", "v1"),
  WorkflowStep.Sbt(
    List("site/publishMicrosite"),
    name = Some("Publish microsite")
  )
)

val catsV = "2.6.1"
val catsEffectV = "2.5.3"
// val shapelessV = "2.3.3"
val fs2V = "2.5.9"
val http4sV = "0.22.4"
val circeV = "0.13.0"
val doobieV = "0.9.4"
val log4catsV = "1.1.1"

val munitCatsEffectV = "1.0.5"
// val specs2V = "4.10.6"

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
  .settings(commonSettings)
  .settings(
    name := "http4s-session"
  )

lazy val examples = project
  .in(file("examples"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings(
    name := "http4s-session-examples",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-dsl" % http4sV,
      "org.http4s" %% "http4s-ember-server" % http4sV,
      "org.http4s" %% "http4s-ember-client" % http4sV,
      "org.http4s" %% "http4s-circe" % http4sV
    )
  )

lazy val site = project
  .in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(MicrositesPlugin)
  .enablePlugins(MdocPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core)
  .settings {
    import microsites._
    Seq(
      micrositeName := "http4s-session",
      micrositeDescription := "Htp4s Session Management",
      micrositeAuthor := "Christopher Davenport",
      micrositeGithubOwner := "http4s",
      micrositeGithubRepo := "http4s-session",
      micrositeBaseUrl := "/http4s-session",
      micrositeDocumentationUrl := "https://www.javadoc.io/doc/org.http4s/http4s-session_2.13",
      micrositeGitterChannelUrl := "http4s/http4s", // Feel Free to Set To Something Else
      micrositeFooterText := None,
      micrositeHighlightTheme := "atom-one-light",
      micrositePalette := Map(
        "brand-primary" -> "#3e5b95",
        "brand-secondary" -> "#294066",
        "brand-tertiary" -> "#2d5799",
        "gray-dark" -> "#49494B",
        "gray" -> "#7B7B7E",
        "gray-light" -> "#E5E5E6",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#FFFFFF"
      ),
      micrositePushSiteWith := GitHub4s,
      micrositeGithubToken := sys.env.get("GITHUB_TOKEN"),
      micrositeExtraMdFiles := Map(
        file("CODE_OF_CONDUCT.md") -> ExtraMdFileConfig(
          "code-of-conduct.md",
          "page",
          Map("title" -> "code of conduct", "section" -> "code of conduct", "position" -> "100")
        ),
        file("LICENSE") -> ExtraMdFileConfig("license.md",
                                             "page",
                                             Map("title" -> "license", "section" -> "license", "position" -> "101")
        )
      )
    )
  }

// General Settings
lazy val commonSettings = Seq(
  testFrameworks += new TestFramework("munit.Framework"),
  libraryDependencies ++= {
    if (ScalaArtifacts.isScala3(scalaVersion.value)) Seq.empty
    else
      Seq(
        compilerPlugin(("org.typelevel" % "kind-projector" % kindProjectorV).cross(CrossVersion.full)),
        compilerPlugin("com.olegpy" %% "better-monadic-for" % betterMonadicForV)
      )
  },
  scalacOptions ++= {
    if (ScalaArtifacts.isScala3(scalaVersion.value)) Seq("-source:3.0-migration")
    else Seq()
  },
  Compile / doc / sources := {
    val old = (Compile / doc / sources).value
    if (ScalaArtifacts.isScala3(scalaVersion.value))
      Seq()
    else
      old
  },
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % catsV,
    "org.typelevel" %% "cats-effect" % catsEffectV,
    "co.fs2" %% "fs2-core" % fs2V,
    "co.fs2" %% "fs2-io" % fs2V,
    "io.chrisdavenport" %% "random" % "0.0.2",
    "io.chrisdavenport" %% "mapref" % "0.1.1",
    "org.http4s" %% "http4s-core" % http4sV,
    "org.typelevel" %%% "munit-cats-effect-2" % munitCatsEffectV % Test
  )
)

// General Settings
inThisBuild(
  List(
    organization := "org.http4s",
    developers := List(
      Developer("ChristopherDavenport",
                "Christopher Davenport",
                "chris@christopherdavenport.tech",
                url("https://github.com/ChristopherDavenport")
      )
    ),
    homepage := Some(url("https://github.com/http4s/http4s-session")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ => false },
    scalacOptions in (Compile, doc) ++= Seq(
      "-groups",
      "-sourcepath",
      (baseDirectory in LocalRootProject).value.getAbsolutePath,
      "-doc-source-url",
      "https://github.com/http4s/http4s-session/blob/v" + version.value + "€{FILE_PATH}.scala"
    )
  )
)
