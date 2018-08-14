package beam.utils

import beam.tags.{ExcludeRegular, Periodic}
import beam.utils.plansampling.PlansSampler
import org.matsim.core.config.ConfigUtils
import org.matsim.core.scenario.{MutableScenario, ScenarioUtils}
import org.matsim.utils.objectattributes.ObjectAttributes
import org.scalatest.{Matchers, WordSpecLike}

class PlansSamplerAppSpec extends WordSpecLike with Matchers {

  val inputData: Array[String] = Array(
    "test/input/sf-light/population.xml",
    "test/input/sf-light/shape/sflight_muni_mask.shp",
    "test/input/sf-light/physsim-network.xml",
    "test/input/sf-light/ind_X_hh_out_test.csv",
    "test/input/sf-light/vehicles.xml",
    "10",
    "output/test/plansampler/",
    "epsg:4326",
    "epsg:26910"
  )

  "PlanSamplerApp class" should {
    "assign available modes to agents " taggedAs (Periodic, ExcludeRegular) in {
      FileUtils.createDirectoryIfNotExists(inputData(6))
      val sampler = PlansSampler
      sampler.init(inputData)
      sampler.run()
      val config = ConfigUtils.createConfig
      config.plans().setInputFile("output/test/plansampler/population.xml.gz")
      config
        .plans()
        .setInputPersonAttributeFile("output/test/plansampler/populationAttributes.xml.gz")
      val dummyScenario: MutableScenario = ScenarioUtils.createMutableScenario(config)
      dummyScenario.setLocked()
      ScenarioUtils.loadScenario(dummyScenario)
      val attributes: ObjectAttributes = dummyScenario.getPopulation.getPersonAttributes

      attributes.getAttribute(
        attributes.toString.split(";")(0).stripPrefix("key="),
        "available-modes"
      ) should equal(
        "car,ride_hail,bike,bus,funicular,gondola,cable_car,ferry,tram,transit,rail,subway,tram"
      )
    }

  }
}
