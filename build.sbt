val releaseV = "1.0.0-SNAPSHOT"

val scala212V = "2.12.18"
val scala213V = "2.13.11"

val scalaV = scala213V
val pekkoV = "1.0.0-RC2"

val MongoJavaDriverVersion = "4.10.0"
val Log4jVersion = "2.20.0"
val NettyVersion = "4.1.94.Final"

val commonDeps = Seq(
  ("org.apache.pekko"  %% "pekko-persistence" % pekkoV)
    .exclude("org.iq80.leveldb", "leveldb")
    .exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
  ("nl.grons" %% "metrics4-scala" % "4.2.9"),
  "org.apache.pekko"         %% "pekko-persistence-query"   % pekkoV     % "compile",
  "org.apache.pekko"         %% "pekko-persistence"         % pekkoV     % "compile",
  "org.apache.pekko"         %% "pekko-actor"               % pekkoV     % "compile",
  "org.mongodb"               % "mongodb-driver-core"       % MongoJavaDriverVersion   % "compile",
  "org.mongodb"               % "mongodb-driver-legacy"     % MongoJavaDriverVersion   % "test",
  "org.slf4j"                 % "slf4j-api"                 % "1.7.36"  % "test",
  "org.apache.logging.log4j"  % "log4j-api"                 % Log4jVersion  % "test",
  "org.apache.logging.log4j"  % "log4j-core"                % Log4jVersion  % "test",
  "org.apache.logging.log4j"  % "log4j-slf4j-impl"          % Log4jVersion  % "test",
  "org.scalatest"             %% "scalatest"                % "3.2.16"   % "test",
  "org.scalatestplus"         %% "mockito-1-10"             % "3.1.0.0" % "test",
  "org.scalatestplus"         %% "junit-4-12"               % "3.2.2.0" % "test",
  "junit"                     % "junit"                     % "4.13.2"    % "test",
  "org.mockito"               % "mockito-all"               % "1.10.19" % "test",
  "org.apache.pekko"         %% "pekko-slf4j"               % pekkoV     % "test",
  "org.apache.pekko"         %% "pekko-testkit"             % pekkoV     % "test",
  "org.apache.pekko"         %% "pekko-persistence-tck"     % pekkoV     % "test",
  "org.apache.pekko"         %% "pekko-cluster-sharding"    % pekkoV     % "test"
)

lazy val Ci = config("ci").extend(Test)

ThisBuild / organization := "com.github.scullxbones"
ThisBuild / version      := releaseV
ThisBuild / scalaVersion := scalaV
ThisBuild / versionScheme := Some("semver-spec")

githubOwner := "scullxbones"
githubRepository := "pekko-persistence-mongo"
githubTokenSource := TokenSource.Environment("GITHUB_TOKEN")

val commonSettings = Seq(
  scalaVersion := scalaV,
  crossScalaVersions := Seq(scala212V, scala213V),
  libraryDependencies ++= commonDeps,
  dependencyOverrides ++= Seq(
    "com.typesafe" % "config" % "1.4.2",
    "org.slf4j" % "slf4j-api" % "1.7.36",
    "org.apache.pekko" %% "pekko-stream" % pekkoV,
    "org.mongodb" % "mongodb-driver-legacy" % MongoJavaDriverVersion
  ),
  libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % VersionScheme.Always,
  version := releaseV,
  organization := "com.github.scullxbones",
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-encoding", "UTF-8",       // yes, this is 2 args
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    // "-Xfatal-warnings",      Deprecations keep from enabling this
    "-Xlint",
    "-Ywarn-dead-code",        // N.B. doesn't work well with the ??? hole
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-target:jvm-1.8"
  ),
  javacOptions ++= Seq(
    "-source", "1.8",
    "-target", "1.8",
    "-Xlint"
  ),
  resolvers ++= Seq(
    "Apache Pekko Staging" at "https://repository.apache.org/content/groups/staging",
//    "Apache Pekko Releases" at "https://repository.apache.org/content/groups/public",
    "Apache Pekko Snapshots" at "https://repository.apache.org/content/groups/snapshots",
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  ),
  Test / parallelExecution := false,
  Test / testOptions += Tests.Argument("-oDS"),
  Ci / testOptions += Tests.Argument("-l", "org.scalatest.tags.Slow"),
  Test / fork := false,
  ThisBuild / publishTo := sonatypePublishTo.value,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)
) ++ inConfig(Ci)(Defaults.testTasks)

lazy val `pekko-persistence-mongo-scala` = (project in file("scala"))
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % MongoJavaDriverVersion % "compile",
      "org.mongodb.scala" %% "mongo-scala-bson"   % MongoJavaDriverVersion % "compile",
      "io.netty"          % "netty-buffer"        % NettyVersion           % "compile",
      "io.netty"          % "netty-transport"     % NettyVersion           % "compile",
      "io.netty"          % "netty-handler"       % NettyVersion           % "compile",
      "org.reactivestreams" % "reactive-streams"  % "1.0.4"
    ),
    dependencyOverrides ++= Seq(
    )
  )
  .configs(Ci)

lazy val `pekko-persistence-mongo-tools` = (project in file("tools"))
  .dependsOn(`pekko-persistence-mongo-scala` % "test->test;compile->compile")
  .settings(commonSettings:_*)
  .settings(
    libraryDependencies ++= Seq(
      "org.mongodb.scala" %% "mongo-scala-driver" % MongoJavaDriverVersion % "compile"
    )
  )
  .configs(Ci)

lazy val `pekko-persistence-mongo` = (project in file("."))
  .aggregate(`pekko-persistence-mongo-scala`, `pekko-persistence-mongo-tools`)
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true,
    publishTo := Some(Resolver.file("file", new File("target/unusedrepo")))
  )
