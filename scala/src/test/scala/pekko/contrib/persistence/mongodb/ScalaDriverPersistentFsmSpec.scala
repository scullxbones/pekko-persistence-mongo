package pekko.contrib.persistence.mongodb

class ScalaDriverPersistentFsmSpec extends PersistentFsmSpec(classOf[ScalaDriverPersistenceExtension], "official-scala")
