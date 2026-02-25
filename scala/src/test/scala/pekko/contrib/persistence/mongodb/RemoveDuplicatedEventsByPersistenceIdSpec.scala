package pekko.contrib.persistence.mongodb

import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterEach, Suite}

trait PekkoStreamFixture extends BeforeAndAfterEach {
  self: Suite =>

  private var _system: ActorSystem = _
  private var _materializer: Materializer = _

  private def config = ConfigFactory.parseString(
    """
      |pekko.extensions = []
    """.stripMargin).withFallback(ConfigFactory.load())

  implicit def system: ActorSystem = Option(_system).getOrElse(throw new IllegalStateException("AtorSystem not started yet"))
  implicit def materializer: Materializer = Option(_materializer).getOrElse(throw new IllegalStateException("Materializer not started yet"))


  override protected def beforeEach(): Unit = {
    super.beforeEach()
    _system = ActorSystem(s"test-${System.currentTimeMillis()}", config)
    _materializer = Materializer(_system)
  }

  override protected def afterEach(): Unit = {
    _materializer.shutdown()
    _system.terminate()
    super.afterEach()
  }
}

class RemoveDuplicatedEventsByPersistenceIdSpec extends BaseUnitTest with ScalaFutures with PekkoStreamFixture {

  "RemoveDuplicatedEventsByPersistenceId" should "not remove non duplicate events" in {

    val events = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 3L, StringPayload("foo", Set())),
      Event("pid-1", 4L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 2L, StringPayload("foo", Set()))
    )

    val processedEvents = Source(events).via(new RemoveDuplicatedEventsByPersistenceId).runFold(Vector.empty[Event])(_ :+ _).futureValue

    processedEvents should contain theSameElementsInOrderAs events
  }

  it should "remove duplicate sequential events" in {

    val events = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 3L, StringPayload("foo", Set())),
      Event("pid-1", 4L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 2L, StringPayload("foo", Set()))
    )

    val expectedEvents = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 3L, StringPayload("foo", Set())),
      Event("pid-1", 4L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 2L, StringPayload("foo", Set()))
    )


    val processedEvents = Source(events).via(new RemoveDuplicatedEventsByPersistenceId).runFold(Vector.empty[Event])(_ :+ _).futureValue

    processedEvents should contain theSameElementsInOrderAs expectedEvents
  }

  it should "remove random duplicate events" in {

    val events = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-1", 3L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 2L, StringPayload("foo", Set())),
      Event("pid-1", 4L, StringPayload("foo", Set()))
    )

    val expectedEvents = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 3L, StringPayload("foo", Set())),
      Event("pid-2", 2L, StringPayload("foo", Set())),
      Event("pid-1", 4L, StringPayload("foo", Set()))
    )


    val processedEvents = Source(events).via(new RemoveDuplicatedEventsByPersistenceId).runFold(Vector.empty[Event])(_ :+ _).futureValue

    processedEvents should contain theSameElementsInOrderAs expectedEvents
  }

  it should "filter duplicates within bounded capacity" in {

    val events = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set()))
    )

    val expectedEvents = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-1", 2L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set()))
    )

    val processedEvents = Source(events).via(new RemoveDuplicatedEventsByPersistenceId(10)).runFold(Vector.empty[Event])(_ :+ _).futureValue

    processedEvents should contain theSameElementsInOrderAs expectedEvents
  }

  it should "allow duplicates for evicted entries when bounded" in {

    // With maxSize=2, after seeing pid-1, pid-2, pid-3, pid-1 gets evicted
    val events = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-3", 1L, StringPayload("foo", Set())),
      Event("pid-1", 1L, StringPayload("foo", Set())) // duplicate, but pid-1 was evicted
    )

    val expectedEvents = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-3", 1L, StringPayload("foo", Set())),
      Event("pid-1", 1L, StringPayload("foo", Set())) // passes through because evicted
    )

    val processedEvents = Source(events).via(new RemoveDuplicatedEventsByPersistenceId(2)).runFold(Vector.empty[Event])(_ :+ _).futureValue

    processedEvents should contain theSameElementsInOrderAs expectedEvents
  }

  it should "preserve legacy unbounded behavior with maxSize 0" in {

    val events = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-3", 1L, StringPayload("foo", Set())),
      Event("pid-1", 1L, StringPayload("foo", Set())), // duplicate — filtered even though 3 pids seen
      Event("pid-2", 2L, StringPayload("foo", Set()))
    )

    val expectedEvents = List(
      Event("pid-1", 1L, StringPayload("foo", Set())),
      Event("pid-2", 1L, StringPayload("foo", Set())),
      Event("pid-3", 1L, StringPayload("foo", Set())),
      Event("pid-2", 2L, StringPayload("foo", Set()))
    )

    val processedEvents = Source(events).via(new RemoveDuplicatedEventsByPersistenceId(0)).runFold(Vector.empty[Event])(_ :+ _).futureValue

    processedEvents should contain theSameElementsInOrderAs expectedEvents
  }

  "RemoveDuplicates" should "re-emit values after eviction when bounded" in {

    val elements = List("a", "b", "c", "a") // "a" evicted after "c" with maxSize=2

    val expectedElements = List("a", "b", "c", "a") // re-emitted

    val processedElements = Source(elements).via(new RemoveDuplicates[String](2)).runFold(Vector.empty[String])(_ :+ _).futureValue

    processedElements should contain theSameElementsInOrderAs expectedElements
  }

  it should "filter duplicates within capacity" in {

    val elements = List("a", "b", "a", "b") // both still in cache with maxSize=2

    val expectedElements = List("a", "b")

    val processedElements = Source(elements).via(new RemoveDuplicates[String](2)).runFold(Vector.empty[String])(_ :+ _).futureValue

    processedElements should contain theSameElementsInOrderAs expectedElements
  }

}
