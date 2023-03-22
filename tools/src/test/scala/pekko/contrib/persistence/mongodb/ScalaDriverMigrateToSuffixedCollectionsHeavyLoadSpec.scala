/*
 * Contributions:
 * Jean-Francois GUENA: implement "suffixed collection name" migration tool
 * ...
 */
package pekko.contrib.persistence.mongodb

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.persistence.PersistentActor
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit._
import org.scalatest.BeforeAndAfterAll
import pekko.contrib.persistence.mongodb.RxStreamsInterop._

import scala.concurrent.{Await, Future, Promise}

class ScalaDriverMigrateToSuffixedCollectionsHeavyLoadSpec extends BaseUnitTest with ContainerMongo with BeforeAndAfterAll {

  override def embedDB = s"migrate-to-suffixed-collections-test"

  override def afterAll(): Unit = cleanup()

  def config(extendedConfig: String = ""): Config = ConfigFactory.parseString(s"""
   |pekko.contrib.persistence.mongodb.mongo.driver = "${classOf[ScalaDriverPersistenceExtension].getName}"
   |pekko.contrib.persistence.mongodb.mongo.mongouri = "mongodb://$host:$noAuthPort/$embedDB"
   |pekko.persistence.journal.plugin = "pekko-contrib-mongodb-persistence-journal"
   |pekko-contrib-mongodb-persistence-journal {
   |    # Class name of the plugin.
   |  class = "pekko.contrib.persistence.mongodb.MongoJournal"
   |}
   |pekko.persistence.snapshot-store.plugin = "pekko-contrib-mongodb-persistence-snapshot"
   |pekko-contrib-mongodb-persistence-snapshot {
   |    # Class name of the plugin.
   |  class = "pekko.contrib.persistence.mongodb.MongoSnapshots"
   |}
   |pekko.contrib.persistence.mongodb.mongo.suffix-migration.heavy-load = true
   |pekko.contrib.persistence.mongodb.mongo.suffix-migration.parallelism = 2
    $extendedConfig
   |""".stripMargin).withFallback(ConfigFactory.defaultReference())

  def props(id: String, promise: Promise[Unit]): Props = Props(new Persistent(id, promise))

  case class Append(s: String)

  class Persistent(val persistenceId: String, completed: Promise[Unit]) extends PersistentActor {
    var events = Vector.empty[String]

    override def receiveRecover: Receive = {
      case s: String => events = events :+ s
    }

    override def receiveCommand: Receive = {
      case Append(s) => persist(s) { str =>
        events = events :+ str
        if (str == "END") {
          completed.success(())
          context.stop(self)
        }
      }
    }
  }

  "A migration process" should "migrate journal to suffixed collections names (heavy load)" in {
    import concurrent.duration._

    // Populate database
    val system1: ActorSystem = ActorSystem("prepare-migration", config())
    implicit val mat1: Materializer = Materializer(system1)
    val ec1 = system1.dispatcher

    val promises = ("foo1" :: "foo2" :: "foo3" :: "foo4" :: "foo5" :: Nil).map(id => id -> Promise[Unit]())
    val ars = promises.map { case (id, p) => system1.actorOf(props(id, p), s"migrate-persistenceId-$id") }

    val end = Append("END")
    ars foreach (_ ! end)

    val futures = promises.map { case (_, p) => p.future }
    val count = Await.result(Future.foldLeft(futures)(0) { case (cnt, _) => cnt + 1 }(ec1), 10.seconds.dilated(system1))
    count shouldBe 5

    val underTest1 = new ScalaMongoDriver(system1, config())
    Await.result(
      Source.future(underTest1.journal)
        .flatMapConcat(_.countDocuments().asPekko)
        .runWith(Sink.head)(mat1),
      10.seconds.dilated(system1)) shouldBe 5

    Await.ready(Future(underTest1.closeConnections())(ec1), 10.seconds.dilated(system1))
    system1.terminate()
    Await.ready(system1.whenTerminated, 3.seconds)

    // perform heavy load migration
    val configExtension = SuffixCollectionNamesTest.extendedConfig
    val system2 = ActorSystem("migration", config(configExtension))
    implicit val mat2: Materializer = Materializer(system2)

    val migrate = new ScalaDriverMigrateToSuffixedCollections()(system2)
    Await.ready(migrate.migrateToSuffixCollections, 10.seconds.dilated(system2))

    system2.terminate()
    Await.ready(system2.whenTerminated, 3.seconds)

    // checking...
    val system3 = ActorSystem("check-migration", config(configExtension))
    implicit val mat3: Materializer = Materializer(system3)
    val ec3 = system3.dispatcher

    val underTest3 = new ScalaMongoDriver(system3, config(configExtension))
    Await.result(
      Source.future(underTest3.journal)
        .flatMapConcat(_.countDocuments().asPekko)
        .runWith(Sink.head)(mat3),
      10.seconds.dilated(system3)) shouldBe 0

    Await.result(
      underTest3.db.listCollectionNames().asPekko
        .runWith(Sink.seq)(mat3),
      10.seconds.dilated(system3)) should contain allOf ("pekko_persistence_journal_foo1-test",
      "pekko_persistence_journal_foo2-test",
      "pekko_persistence_journal_foo3-test",
      "pekko_persistence_journal_foo4-test",
      "pekko_persistence_journal_foo5-test")

    import pekko.contrib.persistence.mongodb.JournallingFieldNames._

    (1 to 5) foreach { id =>
      Await.result(
        Source.future(underTest3.getJournal(s"foo$id"))
          .flatMapConcat(_.countDocuments(org.mongodb.scala.model.Filters.equal(PROCESSOR_ID, s"foo$id")).asPekko)
          .runWith(Sink.head)(mat3),
        10.seconds.dilated(system3)) shouldBe 1
      (1 to 5) filterNot (_ == id) foreach { otherId =>
        Await.result(
          Source.future(underTest3.getJournal(s"foo$otherId"))
            .flatMapConcat(_.countDocuments(org.mongodb.scala.model.Filters.equal(PROCESSOR_ID, s"foo$id")).asPekko)
            .runWith(Sink.head)(mat3),
          10.seconds.dilated(system3)) shouldBe 0
      }
    }

    Await.ready(Future(underTest3.closeConnections())(ec3), 10.seconds.dilated(system3))
    system3.terminate()
    Await.ready(system3.whenTerminated, 3.seconds)
    ()

  }
}
