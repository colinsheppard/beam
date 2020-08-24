package beam.utils.google_routes_db.config

case class GoogleRoutesDBConfig(
  googleapiFiles : scala.List[GoogleRoutesDBConfig.GoogleapiFiles$Elm],
  postgresql     : GoogleRoutesDBConfig.Postgresql
)
object GoogleRoutesDBConfig {
  case class GoogleapiFiles$Elm(
    http  : scala.Option[GoogleRoutesDBConfig.GoogleapiFiles$Elm.Http],
    local : scala.Option[GoogleRoutesDBConfig.GoogleapiFiles$Elm.Local]
  )
  object GoogleapiFiles$Elm {
    case class Http(
      googleTravelTimeEstimationCsvFile : scala.Option[java.lang.String],
      googleapiResponsesJsonFile        : scala.Option[java.lang.String]
    )
    object Http {
      def apply(c: com.typesafe.config.Config): GoogleRoutesDBConfig.GoogleapiFiles$Elm.Http = {
        GoogleRoutesDBConfig.GoogleapiFiles$Elm.Http(
          googleTravelTimeEstimationCsvFile = if(c.hasPathOrNull("googleTravelTimeEstimationCsvFile")) Some(c.getString("googleTravelTimeEstimationCsvFile")) else None,
          googleapiResponsesJsonFile        = if(c.hasPathOrNull("googleapiResponsesJsonFile")) Some(c.getString("googleapiResponsesJsonFile")) else None
        )
      }
    }
          
    case class Local(
      googleTravelTimeEstimationCsvFile : scala.Option[java.lang.String],
      googleapiResponsesJsonFile        : scala.Option[java.lang.String]
    )
    object Local {
      def apply(c: com.typesafe.config.Config): GoogleRoutesDBConfig.GoogleapiFiles$Elm.Local = {
        GoogleRoutesDBConfig.GoogleapiFiles$Elm.Local(
          googleTravelTimeEstimationCsvFile = if(c.hasPathOrNull("googleTravelTimeEstimationCsvFile")) Some(c.getString("googleTravelTimeEstimationCsvFile")) else None,
          googleapiResponsesJsonFile        = if(c.hasPathOrNull("googleapiResponsesJsonFile")) Some(c.getString("googleapiResponsesJsonFile")) else None
        )
      }
    }
          
    def apply(c: com.typesafe.config.Config): GoogleRoutesDBConfig.GoogleapiFiles$Elm = {
      GoogleRoutesDBConfig.GoogleapiFiles$Elm(
        http  = if(c.hasPathOrNull("http")) scala.Some(GoogleRoutesDBConfig.GoogleapiFiles$Elm.Http(c.getConfig("http"))) else None,
        local = if(c.hasPathOrNull("local")) scala.Some(GoogleRoutesDBConfig.GoogleapiFiles$Elm.Local(c.getConfig("local"))) else None
      )
    }
  }
        
  case class Postgresql(
    password : java.lang.String,
    url      : java.lang.String,
    username : java.lang.String
  )
  object Postgresql {
    def apply(c: com.typesafe.config.Config): GoogleRoutesDBConfig.Postgresql = {
      GoogleRoutesDBConfig.Postgresql(
        password = c.getString("password"),
        url      = c.getString("url"),
        username = c.getString("username")
      )
    }
  }
        
  def apply(c: com.typesafe.config.Config): GoogleRoutesDBConfig = {
    GoogleRoutesDBConfig(
      googleapiFiles = $_LGoogleRoutesDBConfig_GoogleapiFiles$Elm(c.getList("googleapiFiles")),
      postgresql     = GoogleRoutesDBConfig.Postgresql(if(c.hasPathOrNull("postgresql")) c.getConfig("postgresql") else com.typesafe.config.ConfigFactory.parseString("postgresql{}"))
    )
  }
  private def $_LGoogleRoutesDBConfig_GoogleapiFiles$Elm(cl:com.typesafe.config.ConfigList): scala.List[GoogleRoutesDBConfig.GoogleapiFiles$Elm] = {
    import scala.collection.JavaConverters._
    cl.asScala.map(cv => GoogleRoutesDBConfig.GoogleapiFiles$Elm(cv.asInstanceOf[com.typesafe.config.ConfigObject].toConfig)).toList
  }
}
      