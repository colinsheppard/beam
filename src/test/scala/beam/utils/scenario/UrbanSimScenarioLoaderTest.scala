package beam.utils.scenario

import beam.sim.common.GeoUtils
import beam.sim.config.BeamConfig
import beam.utils.TestConfigUtils.testConfig
import org.mockito.Mockito.when
import org.scalatest.{AsyncWordSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar

class UrbanSimScenarioLoaderTest extends AsyncWordSpec with Matchers with MockitoSugar {
  val mutableScenario = mock[org.matsim.core.scenario.MutableScenario]
  val beamScenario = mock[beam.sim.BeamScenario]

  val beamConfig = BeamConfig(testConfig("test/input/beamville/beam.conf").resolve())
  when(beamScenario.beamConfig).thenReturn(beamConfig)

  val scenarioSource = mock[ScenarioSource]

  val geoUtils = new GeoUtils {
    override def localCRS: String = "epsg:32631"
  }

  val urbanSimScenario = new UrbanSimScenarioLoader(mutableScenario, beamScenario, scenarioSource, geoUtils)

  "UrbanSimScenarioLoader" should {
    "load properly" in {
      urbanSimScenario.assignVehicles(Seq(), Map.empty, Map.empty)
      1 shouldBe 1
    }
  }

}
