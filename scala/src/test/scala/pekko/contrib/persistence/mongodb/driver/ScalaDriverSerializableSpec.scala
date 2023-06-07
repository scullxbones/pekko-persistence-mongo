package pekko.contrib.persistence.mongodb.driver

import pekko.contrib.persistence.mongodb.{JournalSerializableSpec, SuffixCollectionNamesTest}

class ScalaDriverSerializableSpec extends JournalSerializableSpec(classOf[ScalaDriverPersistenceExtension],"official-scala-ser")

class ScalaDriverSuffixSerializableSpec extends JournalSerializableSpec(classOf[ScalaDriverPersistenceExtension],"official-scala-ser-suffix", SuffixCollectionNamesTest.extendedConfig)

