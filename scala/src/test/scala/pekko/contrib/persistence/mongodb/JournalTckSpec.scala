/* 
 * Contributions:
 * Jean-Francois GUENA: implement "suffixed collection name" feature (issue #39 partially fulfilled)
 * ...
 */

package pekko.contrib.persistence.mongodb

import com.typesafe.config.ConfigFactory
import org.apache.pekko.persistence.CapabilityFlag
import org.apache.pekko.persistence.journal.JournalSpec
import org.scalatest.BeforeAndAfterAll

object JournalTckSpec extends ContainerMongo {

  def config(extensionClass: Class[_], database: String, extendedConfig: String = "|") =
    ConfigFactory.parseString(s"""
     |include "/application.conf"
     |pekko.persistence.journal.plugin = "pekko-contrib-mongodb-persistence-journal"
     |pekko.contrib.persistence.mongodb.mongo.driver = "${extensionClass.getName}"
     |pekko.contrib.persistence.mongodb.mongo.mongouri = "mongodb://$host:$noAuthPort"
     |pekko.contrib.persistence.mongodb.mongo.database = $database
     |pekko-contrib-mongodb-persistence-journal {
     |	  # Class name of the plugin.
     |  class = "pekko.contrib.persistence.mongodb.MongoJournal"
     |}
     $extendedConfig
     |""".stripMargin).withFallback(ConfigFactory.defaultReference()).resolve()

}

abstract class JournalTckSpec(extensionClass: Class[_], dbName: String, extendedConfig: String = "|")
  extends JournalSpec(JournalTckSpec.config(extensionClass, dbName, extendedConfig)) with BeforeAndAfterAll {

  override def supportsRejectingNonSerializableObjects = CapabilityFlag.on()

  override def afterAll() = {
    super.afterAll()
    JournalTckSpec.cleanup(dbName)
  }
}
