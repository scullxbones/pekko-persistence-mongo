/* 
 * Contributions:
 * Jean-Francois GUENA: implement "suffixed collection name" feature (issue #39 partially fulfilled)
 * ...
 */

package pekko.contrib.persistence.mongodb

import com.typesafe.config.ConfigFactory
import org.apache.pekko.persistence.snapshot.SnapshotStoreSpec
import org.scalatest.BeforeAndAfterAll

object SnapshotTckSpec extends ContainerMongo {

  def config(extensionClass: Class[_], database: String, extendedConfig: String = "|") =
    ConfigFactory.parseString(s"""
    |include "/application.conf"
    |pekko.persistence.snapshot-store.plugin = "pekko-contrib-mongodb-persistence-snapshot"
    |pekko.persistence.journal.leveldb.native = off
    |pekko.contrib.persistence.mongodb.driver.mongo.driver = "${extensionClass.getName}"
    |pekko.contrib.persistence.mongodb.driver.mongo.mongouri = "mongodb://$host:$noAuthPort"
    |pekko.contrib.persistence.mongodb.driver.mongo.database = $database
    |pekko-contrib-mongodb-persistence-snapshot {
    |	  # Class name of the plugin.
    |  class = "pekko.contrib.persistence.mongodb.MongoSnapshots"
    |}
    $extendedConfig
    """.stripMargin).withFallback(ConfigFactory.defaultReference()).resolve()
}

abstract class SnapshotTckSpec(extensionClass: Class[_], dbName: String, extendedConfig: String = "|")
  extends SnapshotStoreSpec(SnapshotTckSpec.config(extensionClass,dbName,extendedConfig)) with BeforeAndAfterAll {

  override def beforeAll() = {
    SnapshotTckSpec.cleanup(dbName)
    super.beforeAll()
  }

}
