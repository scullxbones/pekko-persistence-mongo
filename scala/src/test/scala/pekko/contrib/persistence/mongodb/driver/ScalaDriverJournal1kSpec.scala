package pekko.contrib.persistence.mongodb.driver

import pekko.contrib.persistence.mongodb.{Journal1kSpec, SuffixCollectionNamesTest}

class ScalaDriverJournal1kSpec extends Journal1kSpec(classOf[ScalaDriverPersistenceExtension],"officialScalaJournal1k")

class ScalaDriverSuffixJournal1kSpec extends Journal1kSpec(classOf[ScalaDriverPersistenceExtension], "officialScalaSuffixJournal1k", SuffixCollectionNamesTest.extendedConfig)