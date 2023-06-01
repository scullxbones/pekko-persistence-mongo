/* 
 * Contributions:
 * Jean-Francois GUENA: implement "suffixed collection name" feature (issue #39 partially fulfilled)
 * ...
 */

package pekko.contrib.persistence.mongodb

class SuffixCollectionNamesTest extends CanSuffixCollectionNames {
  override def getSuffixFromPersistenceId(persistenceId: String): String = s"$persistenceId-test"  

  override def validateMongoCharacters(input: String): String = {
    // According to mongoDB documentation,
    // forbidden characters in mongoDB collection names (Unix) are /\. "$
    // Forbidden characters in mongoDB collection names (Windows) are /\. "$*<>:|?    
    val forbidden = List('/', '\\', '.', ' ', '\"', '$', '*', '<', '>', ':', '|', '?')

    input.map { c => if (forbidden.contains(c)) '_' else c }
  }
}

object SuffixCollectionNamesTest {
  val extendedConfig = """
    |pekko.contrib.persistence.mongodb.driver.mongo.suffix-builder.class = "pekko.contrib.persistence.mongodb.SuffixCollectionNamesTest"
    |pekko.contrib.persistence.mongodb.driver.mongo.suffix-drop-empty-collections = true
    |""".stripMargin

  def suffixedCollectionName(persistenceId: String): String = {
    val suffix = new SuffixCollectionNamesTest().getSuffixFromPersistenceId(persistenceId)
    s"pekko_persistence_journal_$suffix"
  }
    
  val overriddenConfig = """
    |overrides.suffix-builder.class = "pekko.contrib.persistence.mongodb.SuffixCollectionNamesTest"
    |overrides.suffix-drop-empty-collections = true
    |""".stripMargin
}