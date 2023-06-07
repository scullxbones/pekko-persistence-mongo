package pekko.contrib.persistence.mongodb.driver

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import pekko.contrib.persistence.mongodb.{JournalTckSpec, SuffixCollectionNamesTest}

@RunWith(classOf[JUnitRunner])
class ScalaPersistenceJournalTckSpec extends JournalTckSpec(classOf[ScalaDriverPersistenceExtension], s"officialScalaJournalTck")

@RunWith(classOf[JUnitRunner])
class ScalaSuffixPersistenceJournalTckSpec extends JournalTckSpec(classOf[ScalaDriverPersistenceExtension], s"officialScalaJournalTck-suffix", SuffixCollectionNamesTest.extendedConfig)

