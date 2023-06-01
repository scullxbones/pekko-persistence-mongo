package pekko.contrib.persistence.mongodb

package object driver {

  implicit class NonWrappingLongToInt(val pimped: Long) extends AnyVal {
    def toIntWithoutWrapping: Int = {
      if (pimped > Int.MaxValue) {
        Int.MaxValue
      } else {
        pimped.intValue
      }
    }
  }
}
