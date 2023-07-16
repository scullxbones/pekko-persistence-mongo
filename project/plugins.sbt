libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "2.1.1")

// this would be the plugin to use to publish to GitHub packages maven repo:
//addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.3")
