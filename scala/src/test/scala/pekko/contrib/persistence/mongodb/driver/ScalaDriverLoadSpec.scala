package pekko.contrib.persistence.mongodb.driver

import pekko.contrib.persistence.mongodb.JournalLoadSpec

class ScalaDriverLoadSpec extends JournalLoadSpec(classOf[ScalaDriverPersistenceExtension],"official-scala-load")

