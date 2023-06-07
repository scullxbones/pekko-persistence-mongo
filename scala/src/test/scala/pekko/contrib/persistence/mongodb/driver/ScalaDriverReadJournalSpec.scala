package pekko.contrib.persistence.mongodb.driver

import pekko.contrib.persistence.mongodb.{ReadJournalSpec, SuffixCollectionNamesTest}

class ScalaDriverReadJournalSpec extends ReadJournalSpec(classOf[ScalaDriverPersistenceExtension], "scala-official")

class ScalaDriverSuffixReadJournalSpec extends ReadJournalSpec(classOf[ScalaDriverPersistenceExtension], "scala-official-suffix", SuffixCollectionNamesTest.extendedConfig)
