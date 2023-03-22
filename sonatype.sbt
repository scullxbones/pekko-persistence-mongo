publishMavenStyle := true

licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

pomExtra in Global := {
  <url>https://github.com/scullxbones/pekko-persistence-mongo</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/scullxbones/pekko-persistence-mongo.git</connection>
      <developerConnection>scm:git:git@github.com:scullxbones/pekko-persistence-mongo.git</developerConnection>
      <url>github.com/scullxbones/pekko-persistence-mongo.git</url>
    </scm>
    <developers>
      <developer>
        <id>scullxbones</id>
        <name>Brian Scully</name>
        <url>https://github.com/scullxbones/</url>
      </developer>
      <developer>
        <id>thjaeckle</id>
        <name>Thomas Jäckle</name>
        <url>https://github.com/thjaeckle/</url>
      </developer>
    </developers>
}
