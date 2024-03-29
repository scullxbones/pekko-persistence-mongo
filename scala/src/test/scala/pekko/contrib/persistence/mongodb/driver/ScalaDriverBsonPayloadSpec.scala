package pekko.contrib.persistence.mongodb.driver

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.pekko.actor.{ActorRef, ActorSystem, PoisonPill, Props, Status}
import org.apache.pekko.persistence._
import org.apache.pekko.persistence.query.PersistenceQuery
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.testkit._
import org.junit.runner.RunWith
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonInt32, BsonString, BsonValue}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.junit.JUnitRunner
import pekko.contrib.persistence.mongodb.ConfigLoanFixture.withConfig
import pekko.contrib.persistence.mongodb.{BaseUnitTest, ContainerMongo, MongoReadJournal, ScalaDslMongoReadJournal}

import scala.collection.JavaConverters._
import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class ScalaDriverBsonPayloadSpec extends BaseUnitTest with ContainerMongo with BeforeAndAfterAll with ScalaFutures {

  private val bsonConfig: Config = ConfigFactory.parseString(
    s"""
       |pekko.contrib.persistence.mongodb.mongo {
       |  mongouri = "mongodb://$host:$noAuthPort/$embedDB"
       |  db = "$embedDB"
       |}
       |
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
       |pekko-contrib-mongodb-persistence-readjournal {
       |  # Class name of the plugin.
       |  class = "pekko.contrib.persistence.mongodb.MongoReadJournal"
       |}
       |""".stripMargin).withFallback(ConfigFactory.defaultReference())

  override def embedDB = "official-scala-bson"

  override def beforeAll(): Unit = cleanup()

  private val documents = {
    val msg1 = BsonDocument("a" -> BsonInt32(1), "b" -> BsonString("2"))
    val msg2 = BsonDocument("a" -> BsonInt32(2), "b" -> BsonString("3"))
    msg1 :: msg2 :: Nil
  }

  "An official scala driver" should "support storage of `BsonDocument`s" in withConfig(bsonConfig,"pekko-contrib-mongodb-persistence-journal","scala-payload-config") { case (actorSystem, _) =>
    implicit val as: ActorSystem = actorSystem
    val underTest = actorSystem.actorOf(PayloadSpec.props("documents"))
    val probe = TestProbe()
    probe.send(underTest, PayloadSpec.Command(documents.head))
    probe.send(underTest, PayloadSpec.Command(documents(1)))
    probe.send(underTest, PayloadSpec.Get)
    val contents = probe.expectMsgType[PayloadSpec.Contents]
    contents.payload should contain theSameElementsInOrderAs documents.reverse
  }

  it should "support reading `BsonDocument` contents with read journal" in withConfig(bsonConfig,"pekko-contrib-mongodb-persistence-journal","scala-payload-config") { case (actorSystem, _) =>
    implicit val as: ActorSystem = actorSystem
    implicit val mat: Materializer = Materializer(as)
    val readJournal =
      PersistenceQuery(as).readJournalFor[ScalaDslMongoReadJournal](MongoReadJournal.Identifier)
    val fut = readJournal.currentEventsByPersistenceId("documents", 0, Long.MaxValue)
      .runWith(Sink.seq)
    fut.futureValue(timeout(3.seconds.dilated)).map(_.event) should contain theSameElementsInOrderAs documents
  }

  it should "support restoring persistent state of `BsonDocument`s from snapshot" in withConfig(bsonConfig,"pekko-contrib-mongodb-persistence-journal","scala-payload-config") { case (actorSystem, _) =>
    implicit val as: ActorSystem = actorSystem
    val underTest = actorSystem.actorOf(PayloadSpec.props("documents"))
    val probe = TestProbe()

    probe.send(underTest, PayloadSpec.MakeSnapshot)
    probe.expectMsgPF(5.seconds.dilated, "snapshot response") {
      case Status.Failure(t) => fail(t)
      case Status.Success(sn: Long) =>
        sn shouldBe 2L
    }
    underTest ! PoisonPill

    val restored = actorSystem.actorOf(PayloadSpec.props("documents"))
    probe.send(restored, PayloadSpec.Get)
    val contents = probe.expectMsgType[PayloadSpec.Contents]
    contents.payload should contain theSameElementsInOrderAs documents.reverse
  }

  private val arrays = {
    val msg1 = BsonArray.fromIterable(BsonInt32(1) :: BsonString("2") :: Nil)
    val msg2 = BsonArray.fromIterable(BsonDocument("a" -> BsonInt32(2)) :: BsonDocument("b" -> BsonString("3")) :: Nil)
    msg1 :: msg2 :: Nil
  }

  it should "support storage of `BsonArray`s" in withConfig(bsonConfig,"pekko-contrib-mongodb-persistence-journal","scala-payload-config") { case (actorSystem, _) =>
    implicit val as: ActorSystem = actorSystem
    val underTest = actorSystem.actorOf(PayloadSpec.props("arrays"))
    val probe = TestProbe()
    probe.send(underTest, PayloadSpec.Command(arrays.head))
    probe.send(underTest, PayloadSpec.Command(arrays(1)))
    probe.send(underTest, PayloadSpec.Get)
    val contents = probe.expectMsgType[PayloadSpec.Contents]
    contents.payload should contain theSameElementsInOrderAs arrays.reverse
  }

  it should "support reading `BsonArray` contents with read journal" in withConfig(bsonConfig,"pekko-contrib-mongodb-persistence-journal","scala-payload-config") { case (actorSystem, _) =>
    implicit val as: ActorSystem = actorSystem
    implicit val mat: Materializer = Materializer(as)
    val readJournal =
      PersistenceQuery(as).readJournalFor[ScalaDslMongoReadJournal](MongoReadJournal.Identifier)
    val fut = readJournal.currentEventsByPersistenceId("arrays", 0, Long.MaxValue)
      .runWith(Sink.seq)
    fut.futureValue(timeout(3.seconds.dilated)).map(_.event) should contain theSameElementsInOrderAs arrays
  }

  it should "support restoring persistent state of `BsonArray`s from snapshot" in withConfig(bsonConfig,"pekko-contrib-mongodb-persistence-journal","scala-payload-config") { case (actorSystem, _) =>
    implicit val as: ActorSystem = actorSystem
    val underTest = actorSystem.actorOf(PayloadSpec.props("arrays"))
    val probe = TestProbe()

    probe.send(underTest, PayloadSpec.MakeSnapshot)
    probe.expectMsgPF(5.seconds.dilated, "snapshot response") {
      case Status.Failure(t) => fail(t)
      case Status.Success(sn: Long) =>
        sn shouldBe 2L
    }
    underTest ! PoisonPill

    val restored = actorSystem.actorOf(PayloadSpec.props("arrays"))
    probe.send(restored, PayloadSpec.Get)
    val contents = probe.expectMsgType[PayloadSpec.Contents]
    contents.payload should contain theSameElementsInOrderAs arrays.reverse
  }

}

object PayloadSpec {
  def props(persistenceId: String): Props =
    Props(new BsonPayloadActor(persistenceId))

  case class Command(payload: BsonValue)
  case object Get
  case object MakeSnapshot
  case class Contents(payload: List[BsonValue])

  class BsonPayloadActor(val persistenceId: String) extends PersistentActor {

    private var state = List.empty[BsonValue]

    override def receiveRecover: Receive = {
      case bson:BsonValue =>
        state ::= bson
      case SnapshotOffer(_, arr: BsonArray) =>
        state = arr.getValues.asScala.toList
    }

    override def receiveCommand: Receive = snapshotHandling(None)

    private def snapshotHandling(ref: Option[ActorRef]): Receive = stateless orElse {
      case MakeSnapshot =>
        saveSnapshot(BsonArray.fromIterable(state))
        context.become(snapshotHandling(Option(sender())))
      case SaveSnapshotSuccess(m) =>
        deleteMessages(m.sequenceNr) // clean journal for later testing of snapshot restore
      case SaveSnapshotFailure(_, c) =>
        ref.foreach(_ ! Status.Failure(c))
        context.become(snapshotHandling(None))
      case DeleteMessagesSuccess(sn) =>
        ref.foreach(_ ! Status.Success(sn))
        context.become(snapshotHandling(None))
      case DeleteMessagesFailure(c, _) =>
        ref.foreach(_ ! Status.Failure(c))
        context.become(snapshotHandling(None))
    }

    private def stateless: Receive = {
      case Command(bson) =>
        persist(bson)(ev => state ::= ev)
      case Get =>
        sender() ! Contents(state)
    }
  }
}
