package pekko.contrib.persistence.mongodb.driver

import com.mongodb.CursorType
import org.apache.pekko.NotUsed
import org.apache.pekko.persistence.query.{NoOffset, Offset}
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import org.apache.pekko.stream.{Attributes, Materializer, Outlet, SourceShape}
import org.bson.types.ObjectId
import org.mongodb.scala._
import org.mongodb.scala.bson._
import org.mongodb.scala.model.Aggregates._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import pekko.contrib.persistence.mongodb.JournallingFieldNames._
import pekko.contrib.persistence.mongodb.driver.RxStreamsInterop._
import pekko.contrib.persistence.mongodb.{Event, JournalStream, MongoPersistenceReadJournallingApi, ObjectIdOffset}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Try

object CurrentAllEvents {
  def source(driver: ScalaMongoDriver): Source[Event, NotUsed] = {

    import driver.ScalaSerializers._

    Source.future(driver.journalCollectionsAsFuture)
      .flatMapConcat(_.map(
        _.find()
          .projection(include(EVENTS))
          .asPekko
          .map(e =>
            Option(e.asDocument().get(EVENTS)).filter(_.isArray).map(_.asArray)
              .map(
                _.getValues.asScala.collect{
                  case d:BsonDocument => driver.deserializeJournal(d)
                })
              .getOrElse(Nil)
          ).mapConcat(_.toVector)
      ).reduceLeftOption(_ concat _)
       .getOrElse(Source.empty))
  }
}

object CurrentPersistenceIds {
  def source(driver: ScalaMongoDriver): Source[String, NotUsed] = {
    Source.future(driver.journalCollectionsAsFuture)
      .mapConcat(identity)
      .flatMapConcat(_.aggregate(project(include(PROCESSOR_ID)) :: group(s"$$$PROCESSOR_ID") :: Nil).asPekko)
      .map { doc => Option(doc.getString("_id").getValue).getOrElse("") }
      .filterNot(_.isEmpty)
  }
}

object CurrentEventsByPersistenceId {
  def queryFor(persistenceId: String, fromSeq: Long, toSeq: Long): conversions.Bson =
    and(
      equal(PROCESSOR_ID, persistenceId),
      gte(TO, fromSeq),
      lte(FROM, toSeq)
    )

  def source(driver: ScalaMongoDriver, persistenceId: String, fromSeq: Long, toSeq: Long): Source[Event, NotUsed] = {

    import driver.ScalaSerializers._

    val query = queryFor(persistenceId, fromSeq, toSeq)

    Source.future(driver.getJournal(persistenceId))
      .flatMapConcat(
        _.find(query)
          .sort(ascending(TO))
          .projection(include(EVENTS))
          .asPekko
      ).map(_.asDocument)
       .map(doc =>
        Option(doc.get(EVENTS)).filter(_.isArray).map(_.asArray)
          .map(_.getValues
            .asScala
            .collect{
              case d:BsonDocument => driver.deserializeJournal(d)
            })
          .getOrElse(Nil)
      ).mapConcat(_.toList)
  }
}

object CurrentEventsByTag {
  def source(driver: ScalaMongoDriver, tag: String, fromOffset: Offset): Source[(Event, Offset), NotUsed] = {

    import driver.ScalaSerializers._

    val offset = fromOffset match {
      case NoOffset => None
      case ObjectIdOffset(hexStr, _) => Try(BsonObjectId(new ObjectId(hexStr))).toOption
    }
    val query = and(
      equal(TAGS, tag) :: Nil ++ offset.map(gt(ID, _)) : _*
    )

    Source
      .future(driver.journalCollectionsAsFuture)
      .flatMapConcat(
        _.map(_.find(query).sort(ascending(ID)).asPekko)
         .reduceLeftOption(_ ++ _)
         .getOrElse(Source.empty[BsonDocument])
      ).map(_.asDocument)
       .map{ doc =>
        val id = doc.getObjectId(ID).getValue
        Option(doc.get(EVENTS)).filter(_.isArray).map(_.asArray)
          .map(_.getValues
                .asScala
                .collect{
                  case d:BsonDocument =>
                    driver.deserializeJournal(d) -> ObjectIdOffset(id.toHexString, id.getDate.getTime)
                }
                .filter{
                  case (ev,_) => ev.tags.contains(tag)
                })
          .getOrElse(Nil)
      }.mapConcat(_.toList)
  }
}

class ScalaDriverRealtimeGraphStage(driver: ScalaMongoDriver, bufsz: Int = 16)(factory: Option[BsonObjectId] => FindObservable[BsonDocument])
  extends GraphStage[SourceShape[BsonDocument]] {

  private val out = Outlet[BsonDocument]("out")

  override def shape: SourceShape[BsonDocument] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      @volatile private var lastId: Option[BsonObjectId] = None
      @volatile private var subscription: Option[Subscription] = None
      @volatile private var buffer: List[BsonDocument] = Nil
      private var currentCursor: Option[FindObservable[BsonDocument]] = None
      // TODO TJ currentCursor is never used?

      override def preStart(): Unit = {
        currentCursor = Option(buildCursor(buildObserver))
      }

      override def postStop(): Unit =
        subscription.foreach(s => if (!s.isUnsubscribed) s.unsubscribe())

      private def onNextAc = getAsyncCallback[BsonDocument] { result =>
        if (isAvailable(out)) {
          push(out, result)
          subscription.foreach(_.request(1L))
        }
        else
          buffer = buffer ::: List(result)
        lastId = Option(result.get("_id")).map(_.asObjectId())
      }

      private def onSubAc = getAsyncCallback[Subscription]{ _subscription =>
        _subscription.request(bufsz.toLong)
        subscription = Option(_subscription)
      }

      private def onErrAc = getAsyncCallback[Throwable](failStage)

      private def onCompleteAc = getAsyncCallback[Unit]{ _ =>
        subscription.foreach(_.unsubscribe())
        currentCursor = None
        currentCursor = Option(buildCursor(buildObserver))
      }

      def buildObserver: Observer[BsonDocument] = new Observer[BsonDocument] {
        private val nextAc = onNextAc
        private val errAc = onErrAc
        private val subAc = onSubAc
        private val cmpAc = onCompleteAc

        override def onSubscribe(subscription: Subscription): Unit =
          subAc.invoke(subscription)

        override def onNext(result: BsonDocument): Unit =
          nextAc.invoke(result)

        override def onError(e: Throwable): Unit =
          errAc.invoke(e)

        override def onComplete(): Unit =
          cmpAc.invoke(())
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          while (buffer.nonEmpty && isAvailable(out)){
            val head :: tail = buffer
            push(out, head)
            buffer = tail
            subscription.foreach(_.request(1L))
          }
        }

        override def onDownstreamFinish(): Unit = {
          subscription.foreach(s => if (!s.isUnsubscribed) s.unsubscribe())
          completeStage()
        }
      })

      private def buildCursor(observer: Observer[BsonDocument]): FindObservable[BsonDocument] = {
        subscription.foreach(s => if (!s.isUnsubscribed) s.unsubscribe())
        val c = factory(lastId)
        c.subscribe(observer)
        c
      }
    }

}

class ScalaDriverJournalStream(driver: ScalaMongoDriver) extends JournalStream[Source[(Event, Offset), NotUsed]] {

  import driver.ScalaSerializers._
  implicit val ec: ExecutionContext = driver.querySideDispatcher

  private val cursorBuilder: FindObservable[BsonDocument] => FindObservable[BsonDocument] =
    _.cursorType(CursorType.TailableAwait)
     .maxAwaitTime(30.seconds)

  def cursor(query: Option[conversions.Bson]): Source[(Event, Offset),NotUsed] = {
    if (driver.realtimeEnablePersistence)
      Source.future(driver.realtime)
        .flatMapConcat { rt =>
          Source.fromGraph(
            new ScalaDriverRealtimeGraphStage(driver)(maybeLastId => {
              (query, maybeLastId) match {
                case (Some(q), None) =>
                  cursorBuilder(rt.find(q))
                case (Some(q), Some(id)) =>
                  cursorBuilder(rt.find(and(q, gte("_id", id))))
                case (None, None) =>
                  cursorBuilder(rt.find())
                case (None, Some(id)) =>
                  cursorBuilder(rt.find(gte("_id", id)))
              }
          }).named("rt-graph-stage").async)
          .via(killSwitch.flow)
          .mapConcat[(Event, Offset)] { d =>
            val id = d.getObjectId(ID).getValue
            Option(d.get(EVENTS)).filter(_.isArray).map(_.asArray).map(_.getValues.asScala.collect {
              case d: BsonDocument =>
                driver.deserializeJournal(d) -> ObjectIdOffset(id.toHexString, id.getDate.getTime)
            }.toList).getOrElse(Nil)
          }
        }
        .named("rt-cursor-source")
    else
      Source.empty
  }
}

class ScalaDriverPersistenceReadJournaller(driver: ScalaMongoDriver) extends MongoPersistenceReadJournallingApi {
  val journalStream: ScalaDriverJournalStream = {
    val stream = new ScalaDriverJournalStream(driver)
    driver.actorSystem.registerOnTermination( stream.stopAllStreams() )
    stream
  }

  override def currentAllEvents(implicit m: Materializer, ec: ExecutionContext): Source[Event, NotUsed] =
    CurrentAllEvents.source(driver)

  override def currentPersistenceIds(implicit m: Materializer, ec: ExecutionContext): Source[String, NotUsed] =
    CurrentPersistenceIds.source(driver)

  override def currentEventsByPersistenceId(persistenceId: String, fromSeq: Long, toSeq: Long)(implicit m: Materializer, ec: ExecutionContext): Source[Event, NotUsed] =
    CurrentEventsByPersistenceId.source(driver, persistenceId, fromSeq, toSeq)

  override def currentEventsByTag(tag: String, offset: Offset)(implicit m: Materializer, ec: ExecutionContext): Source[(Event, Offset), NotUsed] =
    CurrentEventsByTag.source(driver, tag, offset)

  override def checkOffsetIsSupported(offset: Offset): Boolean =
    PartialFunction.cond(offset){
      case NoOffset => true
      case ObjectIdOffset(hexStr, _) => ObjectId.isValid(hexStr)
    }

  override def liveEvents(implicit m: Materializer, ec: ExecutionContext): Source[Event, NotUsed] =
    journalStream.cursor(None).map{ case(e,_) => e }

  override def livePersistenceIds(implicit m: Materializer, ec: ExecutionContext): Source[String, NotUsed] =
    journalStream.cursor(None).map{ case(e,_) => e.pid }

  override def liveEventsByPersistenceId(persistenceId: String)(implicit m: Materializer, ec: ExecutionContext): Source[Event, NotUsed] =
    journalStream.cursor(
      Option(equal(PROCESSOR_ID, persistenceId))
    ).mapConcat{ case(ev,_) => List(ev).filter(_.pid == persistenceId) }

  override def liveEventsByTag(tag: String, offset: Offset)(implicit m: Materializer, ec: ExecutionContext, ord: Ordering[Offset]): Source[(Event, Offset), NotUsed] =
    journalStream.cursor(
      Option(equal(TAGS, tag))
    ).filter{ case(ev, off) => ev.tags.contains(tag) &&  ord.gt(off, offset)}

}
