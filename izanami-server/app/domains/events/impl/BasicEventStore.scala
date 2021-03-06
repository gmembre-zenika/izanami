package domains.events.impl

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, Materializer}
import akka.{Done, NotUsed}
import cats.Applicative
import domains.Domain.Domain
import domains.events.EventLogger._
import domains.events.EventStore
import domains.events.Events.IzanamiEvent
import libs.streams.CacheableQueue

import scala.concurrent.Future
import scala.util.Try

class BasicEventStore[F[_]: Applicative](implicit system: ActorSystem) extends EventStore[F] {

  private implicit val mat: Materializer = ActorMaterializer()

  logger.info("Starting default event store")

  private val queue = CacheableQueue[IzanamiEvent](500, queueBufferSize = 500)
  system.actorOf(EventStreamActor.props(queue))

  override def publish(event: IzanamiEvent): F[Done] = {
    //Already published
    system.eventStream.publish(event)
    Applicative[F].pure(Done)
  }

  override def events(domains: Seq[Domain],
                      patterns: Seq[String],
                      lastEventId: Option[Long]): Source[IzanamiEvent, NotUsed] =
    lastEventId match {
      case Some(_) =>
        queue.sourceWithCache
          .via(dropUntilLastId(lastEventId))
          .filter(eventMatch(patterns, domains))
      case None =>
        queue.rawSource
          .filter(eventMatch(patterns, domains))
    }

  override def close() = {}

  override def check(): F[Unit] = Applicative[F].pure(())
}

private[events] object EventStreamActor {
  def props(queue: SourceQueueWithComplete[IzanamiEvent]) =
    Props(new EventStreamActor(queue))
}

private[events] class EventStreamActor(queue: SourceQueueWithComplete[IzanamiEvent]) extends Actor {

  import context.dispatcher

  override def receive = {
    case e: IzanamiEvent =>
      logger.debug(s"New event : $e")
      queue.offer(e)
  }

  override def preStart(): Unit = {
    queue
      .watchCompletion()
      .onComplete(_ => Try(context.system.eventStream.unsubscribe(self)))
    context.system.eventStream.subscribe(self, classOf[IzanamiEvent])
  }

  override def postStop(): Unit = {
    context.system.eventStream.unsubscribe(self)
    queue.complete()
  }
}
