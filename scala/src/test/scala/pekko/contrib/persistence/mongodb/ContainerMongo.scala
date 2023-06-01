package pekko.contrib.persistence.mongodb

import com.mongodb._
import com.mongodb.client.{MongoCollection, MongoDatabase}
import org.bson.Document

trait ContainerMongo {
  def host = sys.env.getOrElse("CONTAINER_HOST","localhost")
  def authPort = 28117
  def noAuthPort = 27117
  def envMongoVersion = Option(sys.env.getOrElse("MONGODB_VERSION","5.0"))

  def embedDB: String = "pekko_persist_mongo_test"
  def mongoClient =  new MongoClient(host,noAuthPort)
  def mongoDatabase: MongoDatabase = mongoClient.getDatabase(embedDB)
  def mongoCollection(named: String): MongoCollection[Document] = mongoDatabase.getCollection(named)
  def pekkoPersistenceJournal: MongoCollection[Document] = mongoDatabase.getCollection("pekko_persistence_journal")

  def cleanup(dbName: String = embedDB): Unit = {
    println(s"Cleaning up db named $dbName")
    try {
      mongoClient.dropDatabase(dbName)
      mongoClient.close()
    } catch {
      case x: Throwable => x.printStackTrace()
    }
  }
}
