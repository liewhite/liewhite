ThisBuild / organization     := "io.github.liewhite"
ThisBuild / organizationName := "liewhite"
ThisBuild / version       := sys.env.get("RELEASE_VERSION").getOrElse("0.4.3")
ThisBuild / scalaVersion  := "3.2.2"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"
ThisBuild / publishTo              := sonatypePublishToBundle.value
sonatypeCredentialHost             := "s01.oss.sonatype.org"
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"

val zioVersion     = "2.0.10"
val zioJsonVersion = "0.4.2"

lazy val common = project
  .in(file("common"))
  .settings(
    name                                   := "common",
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.29" % Test,
  )

lazy val sqlx = project
  .in(file("sqlx"))
  .settings(
    name                                        := "sqlx",
    libraryDependencies += "org.typelevel" %% "shapeless3-deriving" % "3.0.3",
    libraryDependencies += "org.jetbrains"  % "annotations"         % "23.0.0",
    libraryDependencies += "dev.zio" %% "zio"                  % zioVersion,
    libraryDependencies += "dev.zio" %% "zio-json"             % zioJsonVersion,
    libraryDependencies += "mysql"    % "mysql-connector-java" % "8.0.28",
    libraryDependencies += "org.postgresql" % "postgresql" % "42.3.3",
    libraryDependencies += "org.jooq"       % "jooq"       % "3.17.7",
    libraryDependencies += "org.jooq"       % "jooq-meta"  % "3.17.7",
    libraryDependencies += "com.zaxxer"     % "HikariCP"   % "5.0.1",
    libraryDependencies += "org.scalameta" %% "munit"      % "0.7.29" % Test
  ).dependsOn(common)

lazy val root = project
  .in(file("."))
  .settings(
    publish / skip := true,
  )
  .aggregate(sqlx,common)
