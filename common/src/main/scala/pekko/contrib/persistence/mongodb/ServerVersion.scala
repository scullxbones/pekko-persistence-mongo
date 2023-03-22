package pekko.contrib.persistence.mongodb

sealed abstract class ServerVersion(val major: Double) {
  def minor: String
  def atLeast(min: ServerVersion): Boolean =
    ServerVersion.Ordering.gteq(this, min)
}
object ServerVersion {
  private val extract = "^(\\d\\.\\d)\\.(.*)".r
  def unapply(version: String): Option[ServerVersion] = {
    version match {
      case extract(major, minor) if major == "4.4" => Option(`4.4`(minor))
      case extract(major, minor) if major == "5.0" => Option(`5.0`(minor))
      case extract(major, minor) if major == "6.0" => Option(`6.0`(minor))
      case extract(major, minor) => Option(Unsupported(major.toDouble, minor))
      case _ => None
    }
  }

  final case class `4.4`(minor: String) extends ServerVersion(4.4)
  final val `4.4.0` = `4.4`("0")
  final case class `5.0`(minor: String) extends ServerVersion(5.0)
  final val `5.0.0` = `5.0`("0")
  final case class `6.0`(minor: String) extends ServerVersion(6.0)
  final val `6.0.0` = `6.0`("0")
  final case class Unsupported(_major: Double, minor: String) extends ServerVersion(_major)

  implicit object Ordering extends Ordering[ServerVersion] {
    override def compare(x: ServerVersion, y: ServerVersion): Int = {
      val major = x.major.compareTo(y.major)
      if (major == 0)
        compareMinors(x.minor, y.minor)
      else
        major
    }

    private def compareMinors(minorX: String, minorY: String): Int = {
      minorX.split('.').zipAll(minorY.split('.'), "0", "0").foldLeft(0){ case (last, (x,y)) =>
        if (last == 0) {
          x.toInt.compareTo(y.toInt)
        } else last
      }
    }
  }
}
