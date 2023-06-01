package pekko.contrib.persistence.mongodb.driver

import pekko.contrib.persistence.mongodb.{JournalTaggingSpec, SuffixCollectionNamesTest}

class ScalaDriverTaggingSpec
  extends JournalTaggingSpec(classOf[ScalaDriverPersistenceExtension], "official-scala-tagging")

class ScalaDriverSuffixTaggingSpec
  extends JournalTaggingSpec(classOf[ScalaDriverPersistenceExtension], "official-scala-tagging-suffix", SuffixCollectionNamesTest.extendedConfig)
