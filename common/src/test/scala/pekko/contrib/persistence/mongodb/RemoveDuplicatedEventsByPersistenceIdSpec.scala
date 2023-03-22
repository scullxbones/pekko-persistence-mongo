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


}
