package beam.utils.scenario

import java.util

import beam.router.Modes.BeamMode
import beam.sim.BeamServices
import beam.utils.plan.sampling.AvailableModeUtils
import com.typesafe.scalalogging.LazyLogging
import org.matsim.api.core.v01.population.{Person, Population}
import org.matsim.api.core.v01.{Coord, Id, Scenario}
import org.matsim.core.population.PopulationUtils
import org.matsim.core.scenario.MutableScenario
import org.matsim.households.{Household, _}
import org.matsim.vehicles.Vehicle

import scala.collection.JavaConverters._

class ScenarioLoader2(
  var scenario: MutableScenario,
  var beamServices: BeamServices,
  val scenarioSource: ScenarioSource
) extends LazyLogging {

  import ScenarioLoader2._

  private val availableModes: Seq[String] = BeamMode.allModes.map(_.value)

  private lazy val plans: Iterable[PlanElement] = {
    val r = scenarioSource.getPlans
    logger.info(s"Read ${r.size} plans")
    r
  }

  private def personsWithPlans: Iterable[PersonInfo] = {
    val persons: Iterable[PersonInfo] = scenarioSource.getPersons
    val personIdsWithPlan = plans.map(_.personId).toSet
    val result = persons.filter(person => personIdsWithPlan.contains(person.personId))
    logger.info(s"There are ${persons.size} people. ${result.size} have plans")
    result
  }

  def loadScenario(): Scenario = {
    logger.info("The scenario loading started...")


    val scenarioPopulation = replacePersonsAndPersonsAttributesFromPopulation(scenario.getPopulation, personsWithPlans)
    replacePlansFromPopulation(scenarioPopulation, plans)

    val newHouseholds: Iterable[Household] = buildMatsimHouseholds(scenarioSource.getHousehold, personsWithPlans, scenarioSource.getVehicles)
    val households = replaceHouseholds(scenario.getHouseholds, newHouseholds)

    beamServices.personHouseholds = buildServicesPersonHouseholds(households)


    logger.info("The scenario loading is completed.")
    scenario
  }

  private def replaceHouseholds(households: Households, newHouseholds: Iterable[Household]): Households = {
    logger.info("Applying households...")

    val matsimHouseholds = newHouseholds.map {
      hh => (hh.getId, hh)
    }.toMap.asJava

    households.getHouseholds.clear()
    households.getHouseholds.putAll(matsimHouseholds)
    households
  }

  private[utils] def buildAttributes(households: Iterable[HouseholdInfo]): Map[String, (String, Any)] = {
    households.flatMap { householdInfo =>
      val newId = householdInfo.householdId.id

      val coord = buildCoordinates(householdInfo)
      Seq(
        newId -> ("homecoordx", coord.getX),
        newId -> ("homecoordy", coord.getY)
      )
    }.toMap
  }

  private def buildCoordinates(householdInfo: HouseholdInfo) = {
    if (beamServices.beamConfig.beam.exchange.scenario.convertWgs2Utm) {
      beamServices.geo.wgs2Utm(new Coord(householdInfo.x, householdInfo.y))
    } else {
      new Coord(householdInfo.x, householdInfo.y)
    }
  }

  private[utils] def replacePersonsAndPersonsAttributesFromPopulation(population: Population, persons: Iterable[PersonInfo]): Population = {
    logger.info("Applying persons...")
    population.getPersons.clear()
    population.getPersonAttributes.clear()

    persons.foreach { personInfo =>
      val person = population.getFactory.createPerson(Id.createPersonId(personInfo.personId.id)) // TODO: find way to create a person without previous instance

      val personId = person.getId.toString

      val personAttrib = population.getPersonAttributes
      personAttrib.putAttribute(personId, "householdId", personInfo.householdId)
      personAttrib.putAttribute(personId, "rank", personInfo.rank)
      personAttrib.putAttribute(personId, "age", personInfo.age)

      AvailableModeUtils.setAvailableModesForPerson_v2(beamServices, person, population, availableModes)

      population.addPerson(person)
    }

    population
  }

  private[utils] def replacePlansFromPopulation(population: Population, plans: Iterable[PlanElement]): Population = {
    logger.info("Applying plans...")

    plans.foreach { planInfo =>
      val person = population.getPersons.get(Id.createPersonId(planInfo.personId.id))
      if (person != null) {
        var plan = person.getSelectedPlan
        if (plan == null) {
          plan = PopulationUtils.createPlan(person)
          person.addPlan(plan)
          person.setSelectedPlan(plan)
        }
        val planElement = planInfo.planElementType
        if (planElement.equalsIgnoreCase("leg")) {
          planInfo.legMode match {
            case Some(mode) =>
              PopulationUtils.createAndAddLeg(plan, mode)
            case None =>
              PopulationUtils.createAndAddLeg(plan, "")
          }
        } else if (planElement.equalsIgnoreCase("activity")) {
          assert(
            planInfo.activityLocationX.isDefined,
            s"planElement is `activity`, but `x` is None! planInfo: $planInfo"
          )
          assert(
            planInfo.activityLocationY.isDefined,
            s"planElement is `activity`, but `y` is None! planInfo: $planInfo"
          )
          val coord = if (beamServices.beamConfig.beam.exchange.scenario.convertWgs2Utm) {
            beamServices.geo.wgs2Utm(new Coord(planInfo.activityLocationX.get, planInfo.activityLocationY.get))
          } else {
            new Coord(planInfo.activityLocationX.get, planInfo.activityLocationY.get)
          }
          val activityType = planInfo.activityType.getOrElse(
            throw new IllegalStateException(
              s"planElement is `activity`, but `activityType` is None. planInfo: $planInfo"
            )
          )
          val act = PopulationUtils.createAndAddActivityFromCoord(plan, activityType, coord)
          planInfo.activityEndTime.foreach { endTime =>
            act.setEndTime(endTime * 60 * 60)
          }
        }
      }
    }

    population
  }

}

object ScenarioLoader2 extends LazyLogging {

  private[utils] def buildMatsimHouseholds(
    households: Iterable[HouseholdInfo],
    people: Iterable[PersonInfo],
    vehicles: Iterable[VehicleInfo]
  ): Iterable[Household] = {
    val householdIdToVehicles = vehicles
      .groupBy(_.householdId)
      .map {
        case (id, vehicleInfo) => HouseholdId(id) -> vehicleInfo
      }

    val householdIdToPersons = people.groupBy(_.householdId)

    households.map { householdInfo =>
      val householdResult = new HouseholdsFactoryImpl().createHousehold(buildHouseholdId(householdInfo))

      householdResult.setIncome(buildIncome(householdInfo))
      householdResult.setMemberIds(buildMemberIdsAsJavaList(householdIdToPersons, householdInfo))
      householdResult.setVehicleIds(buildVehicleIdsAsJavaList(householdIdToVehicles, householdInfo))

      householdResult
    }
  }

  private def buildHouseholdId(householdInfo: HouseholdInfo): Id[Household] = {
    Id.create(householdInfo.householdId.id, classOf[Household])
  }

  private def buildIncome(householdInfo: HouseholdInfo): Income = {
    new IncomeImpl(householdInfo.income, Income.IncomePeriod.year)
  }

  private def buildMemberIdsAsJavaList(
    householdIdToPersons: Map[HouseholdId, Iterable[PersonInfo]],
    householdInfo: HouseholdInfo
  ): util.List[Id[Person]] = {
    householdIdToPersons.get(householdInfo.householdId) match {
      case Some(persons) =>
        persons.map(x => Id.createPersonId(x.personId.id)).toList.asJava
      case None =>
        logger.warn(s"Could not find persons for the `household_id` '${householdInfo.householdId}'")
        util.Collections.emptyList()
    }
  }

  def buildVehicleIdsAsJavaList(
    householdIdToVehicles: Map[HouseholdId, Iterable[VehicleInfo]],
    householdInfo: HouseholdInfo
  ): util.List[Id[Vehicle]] = {
    householdIdToVehicles.get(householdInfo.householdId) match {
      case Some(vehicles) =>
        vehicles.map(x => Id.createVehicleId(x.vehicleId)).toList.asJava
      case None =>
        logger.warn(s"Could not find vehicles for the `household_id` '${householdInfo.householdId}'")
        util.Collections.emptyList()
    }
  }

  def buildServicesPersonHouseholds(households: Households): Map[Id[Person], Household] = {
    households.getHouseholds
      .values()
      .asScala
      .flatMap(h => h.getMemberIds.asScala.map(_ -> h))
      .toMap
  }


}
