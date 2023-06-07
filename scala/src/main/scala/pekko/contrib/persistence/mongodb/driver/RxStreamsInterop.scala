package pekko.contrib.persistence.mongodb.driver

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.{scaladsl => pekkoS}
import org.mongodb.scala._
import org.reactivestreams.Subscriber
import org.{reactivestreams => rx}

import java.util.concurrent.atomic.AtomicBoolean

object RxStreamsInterop {

  implicit class ObservableAdapter[T](val observable: Observable[T]) {
    def asRx: rx.Publisher[T] = RxStreamsAdapter(observable)
    def asPekko: pekkoS.Source[T, NotUsed] =
      pekkoS.Source.fromPublisher(RxStreamsAdapter(observable))
  }

  /*
   * Reactive streams
   */
  case class RxStreamsAdapter[T](observable: Observable[T]) extends rx.Publisher[T] {
    override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
      observable.subscribe(new Observer[T] {
        override def onSubscribe(subscription: Subscription): Unit = {
          subscriber.onSubscribe(SubscriptionAdapter(subscriber, subscription))
        }

        override def onNext(result: T): Unit = subscriber.onNext(result)

        override def onError(e: Throwable): Unit = subscriber.onError(e)

        override def onComplete(): Unit = subscriber.onComplete()
      })
    }
  }

  case class SubscriptionAdapter[T](subscriber: Subscriber[_ >: T], subscription: Subscription) extends rx.Subscription {
    private final val cancelled: AtomicBoolean = new AtomicBoolean

    override def request(n: Long): Unit = {
      assert(n > 0, "N must be greater than 0 while subscription is active (not cancelled)")
      subscription.request(n)
    }

    override def cancel(): Unit =
      if (!cancelled.getAndSet(true)) subscription.unsubscribe()

  }
}
