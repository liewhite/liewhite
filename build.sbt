ThisBuild / organization           := "io.github.liewhite"
ThisBuild / organizationName       := "liewhite"
ThisBuild / version                := sys.env.get("RELEASE_VERSION").getOrElse("5.1.1")
ThisBuild / scalaVersion           := "3.5.0"
ThisBuild / versionScheme          := Some("early-semver")
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo              := sonatypePublishToBundle.value
sonatypeCredentialHost             := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository     := "https://s01.oss.sonatype.org/service/local"
ThisBuild / scalacOptions += "-Yretain-trees"

val zioVersion       = "2.1.11"
val zioHttpVersion   = "3.0.1"
val zioSchemaVersion = "1.5.0"

val zioSchemaDeps = Seq(
  "dev.zio" %% "zio-schema"            % zioSchemaVersion,
  "dev.zio" %% "zio-schema-json"       % zioSchemaVersion,
  "dev.zio" %% "zio-schema-derivation" % zioSchemaVersion
)

lazy val json = project
  .in(file("json"))
  .settings(
    name := "json",
    libraryDependencies ++= zioSchemaDeps,
    libraryDependencies += "commons-codec" % "commons-codec" % "1.16.0"
  )
  .dependsOn(common)

lazy val common = project
  .in(file("common"))
  .settings(
    name                                   := "common",
    libraryDependencies += "org.typelevel" %% "shapeless3-deriving" % "3.4.3",
    libraryDependencies += "commons-codec"  % "commons-codec"       % "1.16.0",
    libraryDependencies += "dev.zio"       %% "zio"                 % zioVersion,
    libraryDependencies ++= zioSchemaDeps,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )

val configDeps = Seq(
  "dev.zio"                         %% "zio"                     % zioVersion,
  "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.15.2"
)

lazy val config = project
  .in(file("config"))
  .settings(
    name := "config",
    libraryDependencies ++= configDeps
  )
  .dependsOn(json)

val okHttpDeps = Seq(
  "com.softwaremill.sttp.client3" %% "core"           % "3.6.2",
  "com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.6.2"
)

lazy val quant = project
  .in(file("quant"))
  .settings(
    name := "quant",
    libraryDependencies ++= okHttpDeps,
    // libraryDependencies += "dev.zio" %% "zio-http" % zioHttpVersion
  )
  .dependsOn(common, json)

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(common, config, json, quant)
