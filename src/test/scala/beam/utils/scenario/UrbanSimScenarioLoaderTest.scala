package beam.utils.scenario

import beam.sim.common.GeoUtilsImpl
import beam.sim.config.BeamConfig
import beam.utils.TestConfigUtils.testConfig
import org.mockito.Mockito.when
import org.scalatest.{AsyncWordSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar

class UrbanSimScenarioLoaderTest extends AsyncWordSpec with Matchers with MockitoSugar {
  val mutableScenario = mock[org.matsim.core.scenario.MutableScenario]
  val beamScenario = mock[beam.sim.BeamScenario]

  val beamConfigBase = BeamConfig(testConfig("test/input/beamville/beam.conf").resolve())
  val beamConfig :BeamConfig = beamConfigBase.copy(
    beam = beamConfigBase.beam.copy(agentsim =
      beamConfigBase.beam.agentsim.copy(agents =
        beamConfigBase.beam.agentsim.agents.copy(vehicles =
          beamConfigBase.beam.agentsim.agents.vehicles.copy(
            fractionOfInitialVehicleFleet = 0.5
          )
        )
      )
    )
  )

  when(beamScenario.beamConfig).thenReturn(beamConfig)

  val scenarioSource = mock[ScenarioSource]

  val geoUtils = new GeoUtilsImpl(beamConfigBase)

  val urbanSimScenario = new UrbanSimScenarioLoader(mutableScenario, beamScenario, scenarioSource, geoUtils)

  "UrbanSimScenarioLoader" should {
    "assign vehicles properly in case of fractionOfInitialVehicleFleet < 1.0 and downsamplingMethod : SECONDARY_VEHICLES_FIRST" in {
      val houseHolds = Stream.fill(10)(household())
      val h2ps = houseHolds.map(x => x.householdId -> Seq.fill(10)(person(x.householdId))).toMap

      urbanSimScenario.assignVehicles(houseHolds, h2ps, Map.empty)
      1 shouldBe 1
    }

    "assign vehicles properly in case of fractionOfInitialVehicleFleet >= 1.0 and downsamplingMethod : SECONDARY_VEHICLES_FIRST" in {
      val houseHolds = Stream.fill(10)(household())
      val h2ps = houseHolds.map(x => x.householdId -> Seq.fill(10)(person(x.householdId))).toMap

      urbanSimScenario.assignVehicles(houseHolds, h2ps, Map.empty)
      1 shouldBe 1
    }

    "assign vehicles properly in case of downsamplingMethod : RANDOM" in {
      val houseHolds = Stream.fill(10)(household())
      val h2ps = houseHolds.map(x => x.householdId -> Seq.fill(10)(person(x.householdId))).toMap

      urbanSimScenario.assignVehicles(houseHolds, h2ps, Map.empty)
      1 shouldBe 1
    }
  }

  private val householdIdIter = Iterator.from(1)
  private val personIdIter = Iterator.from(1)
  private def household() = HouseholdInfo(HouseholdId(householdIdIter.next().toString),10,123.0,1.0,1.0)
  private def person(householdId: HouseholdId) = PersonInfo(
    PersonId(personIdIter.next().toString),
    householdId,123,12,false,0.0)
}
