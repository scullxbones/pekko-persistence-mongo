package pekko.contrib.persistence.mongodb

import com.typesafe.config._
import org.apache.pekko.actor.ActorSystem

class MongoSettingsSpec extends BaseUnitTest {

  def reference: Config = ConfigFactory.parseString(
    """
      |pekko.contrib.persistence.mongodb.driver.mongo.driver = foo
    """.stripMargin)

  def withUri: Config = ConfigFactory.parseString(
    """
      |pekko.contrib.persistence.mongodb.driver.mongo.mongouri = "mongodb://appuser:apppass@localhost:27017/sample_db_name"
      |pekko.contrib.persistence.mongodb.driver.mongo.driver = foo
    """.stripMargin)

  def withMultiLegacy: Config = ConfigFactory.parseString(
    """
      |pekko.contrib.persistence.mongodb.driver.mongo.urls = ["mongo1.example.com:27017","mongo2.example.com:27017"]
      |pekko.contrib.persistence.mongodb.driver.mongo.driver = foo
    """.stripMargin)

  def withMultiLegacyAndCreds: Config = ConfigFactory.parseString(
    """
      |pekko.contrib.persistence.mongodb.driver.mongo.urls = ["mongo1.example.com:27017","mongo2.example.com:27017","mongo3.example.com:27017"]
      |pekko.contrib.persistence.mongodb.driver.mongo.username = my_user
      |pekko.contrib.persistence.mongodb.driver.mongo.password = my_pass
      |pekko.contrib.persistence.mongodb.driver.mongo.driver = foo
    """.stripMargin)

  def withCredentialsLegacy: Config = ConfigFactory.parseString(
    """
      |pekko.contrib.persistence.mongodb.driver.mongo.urls = ["mongo1.example.com:27017"]
      |pekko.contrib.persistence.mongodb.driver.mongo.username = user
      |pekko.contrib.persistence.mongodb.driver.mongo.password = pass
      |pekko.contrib.persistence.mongodb.driver.mongo.db = spec_db
      |pekko.contrib.persistence.mongodb.driver.mongo.driver = foo
      """.stripMargin)

  def fixture[A](config: Config)(testCode: MongoSettings => A): A = {
    testCode(MongoSettings(new ActorSystem.Settings(getClass.getClassLoader,config.withFallback(ConfigFactory.defaultReference()),"settings name")))
  }

  "A settings object" should "correctly load the defaults" in fixture(reference) { s =>
    s.MongoUri shouldBe "mongodb://localhost:27017/pekko-persistence"
  }

  it should "correctly load a uri" in fixture(withUri) { s =>
    s.MongoUri shouldBe "mongodb://appuser:apppass@localhost:27017/sample_db_name"
  }

  it should "correctly load a replica set" in fixture(withMultiLegacy) { s =>
    s.MongoUri shouldBe "mongodb://mongo1.example.com:27017,mongo2.example.com:27017/pekko-persistence"
  }

  it should "correctly load a replica set with creds" in fixture(withMultiLegacyAndCreds) { s =>
    s.MongoUri shouldBe "mongodb://my_user:my_pass@mongo1.example.com:27017,mongo2.example.com:27017,mongo3.example.com:27017/pekko-persistence"
  }

  it should "correctly load legacy credentials" in fixture(withCredentialsLegacy) { s =>
    s.MongoUri shouldBe "mongodb://user:pass@mongo1.example.com:27017/spec_db"
  }

  it should "allow for override" in fixture(withUri) { s =>
    val overridden = ConfigFactory.parseString("""
        |mongouri = "mongodb://localhost:27017/override"
      """.stripMargin)
    s.withOverride(overridden).MongoUri shouldBe "mongodb://localhost:27017/override"
    s.withOverride(overridden).Implementation shouldBe "foo"
    s.withOverride(overridden).JournalAutomaticUpgrade shouldBe false
  }
}
