ThisBuild / organization           := "io.github.liewhite"
ThisBuild / organizationName       := "liewhite"
ThisBuild / version                := sys.env.get("RELEASE_VERSION").getOrElse("0.4.3")
ThisBuild / scalaVersion           := "3.2.2"
ThisBuild / versionScheme          := Some("early-semver")
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo              := sonatypePublishToBundle.value
sonatypeCredentialHost             := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository     := "https://s01.oss.sonatype.org/service/local"

val zioVersion     = "2.0.13"
val zioJsonVersion = "0.5.0"

val zioSchemaDeps = Seq(
  "dev.zio" %% "zio-schema"            % "0.4.10",
  "dev.zio" %% "zio-schema-json"       % "0.4.10",
  "dev.zio" %% "zio-schema-derivation" % "0.4.10"
)

lazy val json = project
  .in(file("json"))
  .settings(
    name                                   := "common",
    libraryDependencies ++= zioSchemaDeps,
  )

lazy val common = project
  .in(file("common"))
  .settings(
    name                                   := "common",
    libraryDependencies += "org.typelevel" %% "shapeless3-deriving" % "3.3.0",
    libraryDependencies ++= zioSchemaDeps,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test
  )

lazy val sqlx = project
  .in(file("sqlx"))
  .settings(
    name                                   := "sqlx",
    libraryDependencies += "org.jetbrains"  % "annotations"          % "23.0.0",
    libraryDependencies += "dev.zio"       %% "zio"                  % zioVersion,
    libraryDependencies += "mysql"          % "mysql-connector-java" % "8.0.28",
    libraryDependencies += "org.postgresql" % "postgresql"           % "42.3.3",
    libraryDependencies += "org.jooq"       % "jooq"                 % "3.17.7",
    libraryDependencies += "org.jooq"       % "jooq-meta"            % "3.17.7",
    libraryDependencies += "com.zaxxer"     % "HikariCP"             % "5.0.1"
  )
  .dependsOn(common,json)

val rpcZioDeps = Seq(
  "dev.zio" %% "zio"            % zioVersion,
  "dev.zio" %% "zio-concurrent" % zioVersion,
  "dev.zio" %% "zio-streams"    % zioVersion,
)

lazy val rpc = project
  .in(file("rpc"))
  .settings(
    name := "rpc",
    libraryDependencies ++= rpcZioDeps,
    libraryDependencies ++= zioSchemaDeps,
    libraryDependencies += "com.rabbitmq" % "amqp-client" % "5.17.0"
  ).dependsOn(common,json)

val zioConfigVersion = "4.0.0-RC14"
val configDeps = Seq(
  "dev.zio" %% "zio-config"          % zioConfigVersion,
  "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
  "dev.zio" %% "zio-config-yaml"     % zioConfigVersion
)
lazy val config = project
  .in(file("config"))
  .settings(
    name := "config",
    libraryDependencies ++= configDeps
  )

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true
  )
  .aggregate(sqlx, rpc, common, config,json)
