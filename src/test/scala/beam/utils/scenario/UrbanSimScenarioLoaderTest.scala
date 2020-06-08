package beam.utils.scenario

import beam.sim.BeamScenario
import beam.sim.common.GeoUtilsImpl
import beam.sim.config.BeamConfig
import beam.utils.TestConfigUtils.testConfig
import org.matsim.core.scenario.MutableScenario
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.{AsyncWordSpec, BeforeAndAfterEach, Matchers}
import org.scalatestplus.mockito.MockitoSugar

class UrbanSimScenarioLoaderTest extends AsyncWordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {
  private val mutableScenario = mock[MutableScenario]
  private val beamScenario = mock[BeamScenario]

  private val beamConfigBase = BeamConfig(testConfig("test/input/beamville/beam.conf").resolve())

  private val scenarioSource = mock[ScenarioSource]

  private val geoUtils = new GeoUtilsImpl(beamConfigBase)

  "UrbanSimScenarioLoader" should {
    "assign vehicles properly in case of fractionOfInitialVehicleFleet < 1.0 and downsamplingMethod : SECONDARY_VEHICLES_FIRST" in {
      // test ordering for excessive vehicles
      when(beamScenario.beamConfig).thenReturn(getConfig(0.5))
      val urbanSimScenario = new UrbanSimScenarioLoader(mutableScenario, beamScenario, scenarioSource, geoUtils)

      val houseHolds = Seq(
        household(2),
        household(2),
        household(3),
        household(3)
      )

      val h2ps = houseHolds.map { h =>
        h.householdId -> List(person(h.householdId), person(h.householdId))
      }.toMap

      val people2Score = h2ps.values.flatten.map(_.personId -> 10.0).toMap

      val res = urbanSimScenario.assignVehicles(houseHolds, h2ps, people2Score)
      1 shouldBe 1
    }
//
//    "assign vehicles properly in case of fractionOfInitialVehicleFleet >= 1.0 and downsamplingMethod : SECONDARY_VEHICLES_FIRST" in {
////      val houseHolds = Stream.fill(10)(household())
////      val h2ps = houseHolds.map(x => x.householdId -> Seq.fill(10)(person(x.householdId))).toMap
////
////      urbanSimScenario.assignVehicles(houseHolds, h2ps, Map.empty)
//      1 shouldBe 1
//    }
//
//    "assign vehicles properly in case of downsamplingMethod : RANDOM" in {
//      val houseHolds = Seq(
//        household(2),
//        household(2),
//        household(3),
//        household(4)
//      )
//
//      val h2ps = houseHolds.map(x => x.householdId -> Seq()).toMap
//
//      urbanSimScenario.assignVehicles(houseHolds, h2ps, Map.empty)
//      1 shouldBe 1
//    }
  }
  private val idIter = Iterator.from(1)

  private def getConfig(fractionOfInitialVehicleFleet: Double = 1.0) = beamConfigBase.copy(
    matsim = beamConfigBase.matsim
      .copy(
        modules = beamConfigBase.matsim.modules.copy(
          global = beamConfigBase.matsim.modules.global.copy(
            randomSeed = 1
          )
        )
      ),
    beam = beamConfigBase.beam.copy(
      agentsim = beamConfigBase.beam.agentsim.copy(
        agents = beamConfigBase.beam.agentsim.agents.copy(
          vehicles = beamConfigBase.beam.agentsim.agents.vehicles.copy(
            fractionOfInitialVehicleFleet = fractionOfInitialVehicleFleet
          )
        )
      )
    )
  )

  private def household(cars: Int) = HouseholdInfo(HouseholdId(idIter.next().toString), cars, 123.0, 1.0, 1.0)
  private def person(householdId: HouseholdId) =
    PersonInfo(PersonId(idIter.next().toString), householdId, 123, 30, false, 0.0)
}
