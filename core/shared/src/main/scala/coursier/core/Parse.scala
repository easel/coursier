package coursier.core

import java.util.regex.Pattern.quote
import coursier.core.compatibility._

object Parse {

  def version(s: String): Option[Version] = {
    if (s.isEmpty || s.exists(c => c != '.' && c != '-' && c != '_' && !c.letterOrDigit)) None
    else Some(Version(s))
  }

  def ivyLatestSubRevisionInterval(s: String): Option[VersionInterval] =
    if (s.endsWith(".+")) {
      for {
        from <- version(s.stripSuffix(".+"))
        if from.rawItems.nonEmpty
        last <- Some(from.rawItems.last).collect { case n: Version.Numeric => n }
        // a bit loose, but should do the job
        if from.repr.endsWith(last.repr)
        to <- version(from.repr.stripSuffix(last.repr) + last.next.repr)
        // the contrary would mean something went wrong in the loose substitution above
        if from.rawItems.init == to.rawItems.init
      } yield VersionInterval(Some(from), Some(to), fromIncluded = true, toIncluded = false)
    } else
      None

  def versionInterval(s: String): Option[VersionInterval] = {
    for {
      fromIncluded <- if (s.startsWith("[")) Some(true) else if (s.startsWith("(")) Some(false) else None
      toIncluded <- if (s.endsWith("]")) Some(true) else if (s.endsWith(")")) Some(false) else None
      s0 = s.drop(1).dropRight(1)
      commaIdx = s0.indexOf(',')
      if commaIdx >= 0
      strFrom = s0.take(commaIdx)
      strTo = s0.drop(commaIdx + 1)
      from <- if (strFrom.isEmpty) Some(None) else version(strFrom).map(Some(_))
      to <- if (strTo.isEmpty) Some(None) else version(strTo).map(Some(_))
    } yield VersionInterval(from.filterNot(_.isEmpty), to.filterNot(_.isEmpty), fromIncluded, toIncluded)
  }

  def versionConstraint(s: String): Option[VersionConstraint] = {
    def noConstraint = if (s.isEmpty) Some(VersionConstraint.None) else None

    noConstraint
      .orElse(ivyLatestSubRevisionInterval(s).map(VersionConstraint.Interval))
      .orElse(version(s).map(VersionConstraint.Preferred))
      .orElse(versionInterval(s).map(VersionConstraint.Interval))
  }

  val fallbackConfigRegex = {
    val noPar = "([^" + quote("()") + "]*)"
    "^" + noPar + quote("(") + noPar + quote(")") + "$"
  }.r

  def withFallbackConfig(config: String): Option[(String, String)] =
    Parse.fallbackConfigRegex.findAllMatchIn(config).toSeq match {
      case Seq(m) =>
        assert(m.groupCount == 2)
        val main = config.substring(m.start(1), m.end(1))
        val fallback = config.substring(m.start(2), m.end(2))
        Some((main, fallback))
      case _ =>
        None
    }

}
