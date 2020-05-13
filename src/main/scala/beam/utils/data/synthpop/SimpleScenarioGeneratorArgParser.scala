package beam.utils.data.synthpop

import java.io.File

import beam.utils.data.synthpop.SimpleScenarioGenerator.Arguments
import scopt.OptionParser

import scala.util.Try

object SimpleScenarioGeneratorArgParser {
  private def checkFolder(param: String, path: String): Either[String, Unit] = {
    if (param.isEmpty) {
      Left(s"`$param` cannot be empty")
    } else {
      val folder = new File(path)
      if (!folder.isDirectory) {
        Left(s"`$param` with value ${path} is not folder")
      } else if (!folder.canRead) {
        Left(s"`$param` with value ${path} is not readable, check permissions")
      } else Right(())
    }
  }

  private def checkFile(param: String, path: String): Either[String, Unit] = {
    if (param.isEmpty) {
      Left(s"`$param` cannot be empty")
    } else {
      val file = new File(path)
      if (!file.isFile) {
        Left(s"`$param` with value ${path} is not file")
      } else if (!file.canRead) {
        Left(s"`$param` with value ${path} is not readable, check permissions")
      } else Right(())
    }
  }

  private def buildParser: OptionParser[Arguments] = {
    new scopt.OptionParser[Arguments]("SimpleScenarioGenerator") {
      opt[String]("sythpopDataFolder")
        .action(
          (value, args) => {
            args.copy(sythpopDataFolder = value)
          }
        )
        .validate(value => checkFolder("sythpopDataFolder", value))
        .text("Path to the folder with data generated by Sythpop")
      opt[String]("ctppFolder")
        .action(
          (value, args) => args.copy(ctppFolder = value)
        )
        .validate(value => checkFolder("ctppFolder", value))
        .text("Path to the folder with CTPP data for the states")
      opt[String]("stateCodes")
        .action(
          (value, args) => args.copy(stateCodes = value.split(",").toSet)
        )
        .validate(value => if (value.isEmpty) failure("`stateCodes` should be non empty") else success)
        .text("Comma separated list of US states to consider when work with CTPP data")
      opt[String]("tazShapeFolder")
        .action(
          (value, args) => args.copy(tazShapeFolder = value)
        )
        .validate(value => checkFolder("tazShapeFolder", value))
        .text("Path to the folder with TAZ Shape files per state")
      opt[String]("blockGroupShapeFolder")
        .action(
          (value, args) => args.copy(blockGroupShapeFolder = value)
        )
        .validate(value => checkFolder("blockGroupShapeFolder", value))
        .text("Path to the folder with Block Group Shape files per state")
      opt[String]("congestionLevelDataFile")
        .action(
          (value, args) => args.copy(congestionLevelDataFile = value)
        )
        .validate(value => checkFile("congestionLevelDataFile", value))
        .text(
          "Path to the file with congestion data for the main city/area. This is used to estimate travel time. Source of this data can be https://www.tomtom.com/en_gb/traffic-index/"
        )
      opt[String]("workDurationCsv")
        .action(
          (value, args) => args.copy(workDurationCsv = value)
        )
        .validate(value => checkFile("workDurationCsv", value))
        .text(
          "Path to the file which contains joint distribution for work duration. It can be derived from NHTS data https://nhts.ornl.gov/"
        )
      opt[String]("osmMap")
        .action(
          (value, args) => args.copy(osmMap = value)
        )
        .validate(value => checkFile("osmMap", value))
        .text(
          "Path to OSM PB map. Raw OSM PB map can be downloaded from https://download.geofabrik.de/. But it may require extra steps to crop the map and remove minor roads using OSMOSIS"
        )
      opt[String]("randomSeed")
        .action(
          (value, args) => args.copy(randomSeed = value.toInt)
        )
        .validate(value => if (Try(value.toInt).isFailure) failure("`randomSeed` is not an integer") else success)
        .text("Random seed is used to make the results reproducible")
      opt[String]("offPeakSpeedMetersPerSecond")
        .action(
          (value, args) => args.copy(offPeakSpeedMetersPerSecond = value.toDouble)
        )
        .validate(
          value =>
            if (Try(value.toDouble).isFailure) failure("`offPeakSpeedMetersPerSecond` is not a double")
            else success
        )
        .text("Off peak speed in meters per second. It is used to estimate travel time")
      opt[String]("defaultValueOfTime")
        .action(
          (value, args) => args.copy(defaultValueOfTime = value.toDouble)
        )
        .validate(
          value =>
            if (Try(value.toDouble).isFailure) failure("`defaultValueOfTime` is not a double")
            else success
        )
        .text("Default value of time for Beam")
      opt[String]("localCRS")
        .action(
          (value, args) => args.copy(localCRS = value)
        )
        .validate(
          value =>
            if (value == "") failure("`localCRS` is not a double")
            else success
        )
        .text("Local coordinate reference system ")
      opt[String]("outputFolder")
        .action(
          (value, args) => args.copy(outputFolder = value)
        )
        .validate(value => if (value.isEmpty) failure("`outputFolder` cannot be emtpy") else success)
        .text("Path to output folder with the results")
    }
  }

  private def parseArguments(parser: OptionParser[Arguments], args: Array[String]): Option[Arguments] = {
    parser.parse(args, init = Arguments("", "", Set.empty, "", "", "", "", "", 1, 0.0, 0.0, "", ""))
  }

  def parseArguments(args: Array[String]): Option[Arguments] = parseArguments(buildParser, args)

}
