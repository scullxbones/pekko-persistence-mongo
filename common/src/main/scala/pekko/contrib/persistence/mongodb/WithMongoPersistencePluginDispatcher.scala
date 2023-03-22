package pekko.contrib.persistence.mongodb

import com.typesafe.config.Config
import org.apache.pekko.actor.ActorSystem

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

abstract class WithMongoPersistencePluginDispatcher(actorSystem: ActorSystem, config: Config) {

  implicit lazy val pluginDispatcher: ExecutionContext =
    Try(actorSystem.dispatchers.lookup(config.getString("plugin-dispatcher"))) match {
      case Success(configuredPluginDispatcher) =>
        configuredPluginDispatcher
      case Failure(NonFatal(_)) =>
        actorSystem.log.warning("plugin-dispatcher not configured for pekko-contrib-mongodb-persistence. Using actor system dispatcher.")
        actorSystem.dispatcher
      case Failure(t) => throw t
    }
}
