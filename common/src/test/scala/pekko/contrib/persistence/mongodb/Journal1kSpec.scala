/* 
 * Contributions:
 * Jean-Francois GUENA: implement "suffixed collection name" feature (issue #39 partially fulfilled)
 * ...
 */

package pekko.contrib.persistence.mongodb

import com.typesafe.config.ConfigFactory
import org.apache.pekko.util.Timeout
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.tagobjects.Slow

abstract class Journal1kSpec(extensionClass: Class[_], database: String, extendedConfig: String = "|") extends BaseUnitTest with ContainerMongo with BeforeAndAfterAll with ScalaFutures {

  import ConfigLoanFixture._

  override def embedDB = s"1k-test-$database"

  override def beforeAll() = cleanup()

  def config(extensionClass: Class[_]) = ConfigFactory.parseString(s"""
    |include "/application.conf"
    |pekko.contrib.persistence.mongodb.mongo.use-legacy-serialization = true
    |pekko.contrib.persistence.mongodb.mongo.driver = "${extensionClass.getName}"
    |pekko.contrib.persistence.mongodb.mongo.mongouri = "mongodb://$host:$noAuthPort/$embedDB"
    |pekko.contrib.persistence.mongodb.mongo.breaker.timeout.call = 0s
    |pekko.persistence.journal.plugin = "pekko-contrib-mongodb-persistence-journal"
    |pekko-contrib-mongodb-persistence-journal {
    |	  # Class name of the plugin.
    |  class = "pekko.contrib.persistence.mongodb.MongoJournal"
    |}
    |pekko.persistence.snapshot-store.plugin = "pekko-contrib-mongodb-persistence-snapshot"
    |pekko-contrib-mongodb-persistence-snapshot {
    |	  # Class name of the plugin.
    |  class = "pekko.contrib.persistence.mongodb.MongoSnapshots"
    |}
    $extendedConfig
    |""".stripMargin).withFallback(ConfigFactory.defaultReference()).resolve()

  val id = "123"
  import TestStubActors._
  import Counter._
  import org.apache.pekko.pattern._

  import concurrent.duration._

  "A counter" should "persist the counter to 1000" taggedAs Slow in withConfig(config(extensionClass), "pekko-contrib-mongodb-persistence-journal", "1k-test-persist") { case (as,config) =>
    implicit val askTimeout = Timeout(2.minutes)
    val counter = as.actorOf(Counter.props, id)
    (1 to 1000).foreach(_ => counter ! Inc)
    val result = (counter ? GetCounter).mapTo[Int]
    whenReady(result, timeout(askTimeout.duration + 10.seconds)) { onek =>
      onek shouldBe 1000
    }
  }

  it should "restore the counter back to 1000"  taggedAs Slow in withConfig(config(extensionClass), "pekko-contrib-mongodb-persistence-journal", "1k-test-restore") { case (as, config) =>
    implicit val askTimeout = Timeout(2.minutes)
    val counter = as.actorOf(Counter.props, id)
    val result = (counter ? GetCounter).mapTo[Int]
    whenReady(result, timeout(askTimeout.duration + 10.seconds)) { onek =>
      onek shouldBe 1000
    }
  }
}
