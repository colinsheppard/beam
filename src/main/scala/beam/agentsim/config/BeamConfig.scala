// generated by tscfg 0.8.0 on Tue Mar 28 11:33:46 PDT 2017
// source: src/main/resources/config-template.conf

package beam.agentsim.config

case class BeamConfig(
  beam : BeamConfig.Beam
)
object BeamConfig {
  case class Beam(
    outputs : BeamConfig.Beam.Outputs,
    routing : BeamConfig.Beam.Routing,
    sim     : BeamConfig.Beam.Sim
  )
  object Beam {
    case class Outputs(
      defaultLoggingLevel     : scala.Int,
      eventsFileOutputFormats : java.lang.String,
      explodeEventsIntoFiles  : scala.Boolean,
      outputDirectory         : java.lang.String,
      overrideLoggingLevels   : scala.List[BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm],
      writeEventsInterval     : scala.Int,
      writePlansInterval      : scala.Int
    )
    object Outputs {
      case class OverrideLoggingLevels$Elm(
        classname : java.lang.String,
        value     : scala.Int
      )
      object OverrideLoggingLevels$Elm {
        def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm = {
          BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm(
            classname = if(c.hasPathOrNull("classname")) c.getString("classname") else "beam.playground.metasim.events.ActionEvent",
            value     = if(c.hasPathOrNull("value")) c.getInt("value") else 1
          )
        }
      }
            
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Outputs = {
        BeamConfig.Beam.Outputs(
          defaultLoggingLevel     = if(c.hasPathOrNull("defaultLoggingLevel")) c.getInt("defaultLoggingLevel") else 1,
          eventsFileOutputFormats = if(c.hasPathOrNull("eventsFileOutputFormats")) c.getString("eventsFileOutputFormats") else "csv",
          explodeEventsIntoFiles  = c.hasPathOrNull("explodeEventsIntoFiles") && c.getBoolean("explodeEventsIntoFiles"),
          outputDirectory         = if(c.hasPathOrNull("outputDirectory")) c.getString("outputDirectory") else "/Users/sfeygin/remote_files/beam_outputs/",
          overrideLoggingLevels   = $_LBeamConfig_Beam_Outputs_OverrideLoggingLevels$Elm(c.getList("overrideLoggingLevels")),
          writeEventsInterval     = if(c.hasPathOrNull("writeEventsInterval")) c.getInt("writeEventsInterval") else 1,
          writePlansInterval      = if(c.hasPathOrNull("writePlansInterval")) c.getInt("writePlansInterval") else 0
        )
      }
      private def $_LBeamConfig_Beam_Outputs_OverrideLoggingLevels$Elm(cl:com.typesafe.config.ConfigList): scala.List[BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm] = {
        import scala.collection.JavaConversions._
        cl.map(cv => BeamConfig.Beam.Outputs.OverrideLoggingLevels$Elm(cv.asInstanceOf[com.typesafe.config.ConfigObject].toConfig)).toList
      }
    }
          
    case class Routing(
      gtfs : BeamConfig.Beam.Routing.Gtfs,
      otp  : BeamConfig.Beam.Routing.Otp
    )
    object Routing {
      case class Gtfs(
        crs           : java.lang.String,
        operatorsFile : java.lang.String,
        outputDir     : java.lang.String
      )
      object Gtfs {
        def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Routing.Gtfs = {
          BeamConfig.Beam.Routing.Gtfs(
            crs           = if(c.hasPathOrNull("crs")) c.getString("crs") else "epsg:26910",
            operatorsFile = if(c.hasPathOrNull("operatorsFile")) c.getString("operatorsFile") else "src/main/resources/GTFSOperators.csv",
            outputDir     = if(c.hasPathOrNull("outputDir")) c.getString("outputDir") else "/Users/sfeygin/remote_files/beam_outputs//gtfs"
          )
        }
      }
            
      case class Otp(
        directory : java.lang.String,
        routerIds : scala.List[java.lang.String]
      )
      object Otp {
        def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Routing.Otp = {
          BeamConfig.Beam.Routing.Otp(
            directory = if(c.hasPathOrNull("directory")) c.getString("directory") else "model-inputs/otp",
            routerIds = $_L$_str(c.getList("routerIds"))
          )
        }
      }
            
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Routing = {
        BeamConfig.Beam.Routing(
          gtfs = BeamConfig.Beam.Routing.Gtfs(c.getConfig("gtfs")),
          otp  = BeamConfig.Beam.Routing.Otp(c.getConfig("otp"))
        )
      }
    }
          
    case class Sim(
      sharedInputs   : java.lang.String,
      simulationName : java.lang.String
    )
    object Sim {
      def apply(c: com.typesafe.config.Config): BeamConfig.Beam.Sim = {
        BeamConfig.Beam.Sim(
          sharedInputs   = if(c.hasPathOrNull("sharedInputs")) c.getString("sharedInputs") else "/Users/sfeygin/remote_files/beam_inputs/",
          simulationName = if(c.hasPathOrNull("simulationName")) c.getString("simulationName") else "development"
        )
      }
    }
          
    def apply(c: com.typesafe.config.Config): BeamConfig.Beam = {
      BeamConfig.Beam(
        outputs = BeamConfig.Beam.Outputs(c.getConfig("outputs")),
        routing = BeamConfig.Beam.Routing(c.getConfig("routing")),
        sim     = BeamConfig.Beam.Sim(c.getConfig("sim"))
      )
    }
  }
        
  def apply(c: com.typesafe.config.Config): BeamConfig = {
    BeamConfig(
      beam = BeamConfig.Beam(c.getConfig("beam"))
    )
  }

  private def $_L$_str(cl:com.typesafe.config.ConfigList): scala.List[java.lang.String] = {
    import scala.collection.JavaConversions._
    cl.map(cv => $_str(cv)).toList
  }
  private def $_expE(cv:com.typesafe.config.ConfigValue, exp:java.lang.String) = {
    val u: Any = cv.unwrapped
    new java.lang.RuntimeException(cv.origin.lineNumber +
      ": expecting: " + exp + " got: " +
      (if (u.isInstanceOf[java.lang.String]) "\"" + u + "\"" else u))
  }
  private def $_str(cv:com.typesafe.config.ConfigValue) =
    java.lang.String.valueOf(cv.unwrapped())
}
      
