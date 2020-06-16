package beam.utils.data.synthpop.generators

import java.util.concurrent.TimeUnit

import beam.utils.Statistics
import beam.utils.data.ctpp.JointDistribution
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.math3.random.{MersenneTwister, RandomGenerator}

import scala.util.control.NonFatal

trait WorkedDurationGenerator {

  /** Gives back the next worked duration
    * @param   rangeWhenLeftHome   The range in seconds, in 24 hours, when a person left a home
    * @return Worked duration in seconds
    */
  def next(rangeWhenLeftHome: Range): Int
}

class WorkedDurationGeneratorImpl(val pathToCsv: String, val rndGen: RandomGenerator)
    extends WorkedDurationGenerator
    with LazyLogging {
  private val jd = JointDistribution.fromCsvFile(
    pathToCsv = pathToCsv,
    rndGen = rndGen,
    columnMapping = Map(
      "startTimeIndex" -> JointDistribution.RANGE_COLUMN_TYPE,
      "durationIndex"  -> JointDistribution.RANGE_COLUMN_TYPE,
      "probability"    -> JointDistribution.DOUBLE_COLUMN_TYPE
    )
  )

  /** Gives back the next worked duration
    *
    * @param   rangeWhenLeftHome The range in seconds, in 24 hours, when a person left a home
    * @return Worked duration in seconds
    */
  override def next(rangeWhenLeftHome: Range): Int = {
    val startHour = roundToFraction(rangeWhenLeftHome.start / 3600.0, 2)
    val endHour = roundToFraction(rangeWhenLeftHome.end / 3600.0, 2)
    val startTimeIndexStr = s"$startHour, $endHour"
    try {
      val sample = jd.getSample(true, ("startTimeIndex", Left(startTimeIndexStr)))
      val workDuration = sample("durationIndex").toDouble
      TimeUnit.HOURS.toSeconds(workDuration.toLong).toInt
    } catch {
      case NonFatal(ex) =>
        logger.warn(
          s"Can't compute worked duration. startTimeIndexStr: '$startTimeIndexStr',  rangeWhenLeftHome: $rangeWhenLeftHome. Error: ${ex.getMessage}",
          ex
        )
        TimeUnit.HOURS.toSeconds(7).toInt
    }
  }

  def roundToFraction(x: Double, fraction: Long): Double = (x * fraction).round.toDouble / fraction
}

object WorkedDurationGeneratorImpl {

  def main(args: Array[String]): Unit = {
    val path = """D:\Work\beam\Austin\input\work_activities_all_us.csv"""
    val w = new WorkedDurationGeneratorImpl(path, new MersenneTwister(42))
    val timeWhenLeaveHome = Range(TimeUnit.HOURS.toSeconds(10).toInt, TimeUnit.HOURS.toSeconds(11).toInt)
    val allDurations = (1 to 10000).map { _ =>
      w.next(timeWhenLeaveHome) / 3600.0
    }

    println(s"Duration stats: ${Statistics(allDurations.map(_.toDouble))}")
  }
}
