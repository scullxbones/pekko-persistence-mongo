libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.1.1")
addSbtPlugin("nl.gn0s1s" % "sbt-dotenv" % "3.0.0")
// plugin to use to publish to GitHub packages maven repo:
addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")
