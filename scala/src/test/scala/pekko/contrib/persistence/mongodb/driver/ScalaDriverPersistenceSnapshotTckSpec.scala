package pekko.contrib.persistence.mongodb.driver

import org.junit.runner.RunWith
import org.scalatestplus.junit.JUnitRunner
import pekko.contrib.persistence.mongodb.{SnapshotTckSpec, SuffixCollectionNamesTest}

@RunWith(classOf[JUnitRunner])
class ScalaDriverPersistenceSnapshotTckSpec extends SnapshotTckSpec(classOf[ScalaDriverPersistenceExtension], "officialScalaSnapshotTck")

@RunWith(classOf[JUnitRunner])
class ScalaDriverPersistenceSuffixSnapshotTckSpec extends SnapshotTckSpec(classOf[ScalaDriverPersistenceExtension], "officialScalaSuffixSnapshotTck", SuffixCollectionNamesTest.extendedConfig)
