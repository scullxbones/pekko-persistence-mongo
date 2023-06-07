package pekko.contrib.persistence.mongodb.driver

import pekko.contrib.persistence.mongodb.PersistentFsmSpec

class ScalaDriverPersistentFsmSpec extends PersistentFsmSpec(classOf[ScalaDriverPersistenceExtension], "official-scala")
