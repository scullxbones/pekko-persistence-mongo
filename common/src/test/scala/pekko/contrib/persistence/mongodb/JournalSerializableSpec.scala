/* 
 * Contributions:
 * Jean-Francois GUENA: implement "suffixed collection name" feature (issue #39 partially fulfilled)
 * ...
 */

package pekko.contrib.persistence.mongodb

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.pattern.ask
import org.apache.pekko.persistence.PersistentActor
import org.apache.pekko.testkit._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pekko.contrib.persistence.mongodb.OrderIdActor.{Get, Increment}

import scala.concurrent.duration._


object OrderIdActor {
  sealed trait Command
  case object Increment extends Command
  case object Get extends Command

  sealed trait MyEvent
  case class Incremented(value: Int = 1) extends MyEvent

  def props: Props = Props(new OrderIdActor)
  def name = "order-id"
}

class OrderIdActor extends PersistentActor {
  import OrderIdActor._

  private var state = 0

  private def updateState(ev: MyEvent): Unit = ev match {
    case Incremented(value) => state += value
  }

  override def receiveRecover: Receive = {
    case e:MyEvent => updateState(e)
  }

  override def receiveCommand: Receive = {
    case Increment => persist(Incremented()){
      e =>
        updateState(e)
        sender() ! state
    }
    case Get => sender() ! state
  }
  override def persistenceId: String = "order-id"
}

abstract class JournalSerializableSpec(extensionClass: Class[_], database: String, extendedConfig: String = "|") extends BaseUnitTest with ContainerMongo with BeforeAndAfterAll with ScalaFutures {
  import ConfigLoanFixture._

  override def embedDB = s"serializable-spec-$database"

  override def beforeAll(): Unit = cleanup()

  def config(extensionClass: Class[_]): Config = ConfigFactory.parseString(s"""
    |include "/application.conf"
    |pekko.actor.allow-java-serialization = on
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
    }
    $extendedConfig
    |""".stripMargin).withFallback(ConfigFactory.defaultReference()).resolve()

  "A journal" should "support writing serializable events" in withConfig(config(extensionClass), "pekko-contrib-mongodb-persistence-journal", "ser-spec-write") { case (as,_) =>
    implicit val system: ActorSystem = as
    implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds.dilated, interval = 500.millis.dilated)
    val probe = TestProbe()

    val pa = as.actorOf(OrderIdActor.props)
    probe.send(pa, Increment)
    probe.send(pa, Increment)
    probe.send(pa, Increment)
    probe.send(pa, Increment)
    probe.receiveN(4, 5.seconds.dilated)
    whenReady((pa ? Increment)(5.second.dilated)) {
      _ shouldBe 5
    }
  }

  it should "support restoring serializable events" in withConfig(config(extensionClass), "pekko-contrib-mongodb-persistence-journal", "ser-spec-restore") { case (as,_) =>
    implicit val system: ActorSystem = as
    implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds.dilated, interval = 500.millis.dilated)

    val pa = as.actorOf(OrderIdActor.props)
    whenReady((pa ? Get)(5.second.dilated)) {
      _ shouldBe 5
    }
  }
}
