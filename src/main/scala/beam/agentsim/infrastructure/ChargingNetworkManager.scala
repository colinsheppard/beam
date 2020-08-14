package beam.agentsim.infrastructure

import akka.actor.{Actor, ActorLogging}
import beam.agentsim.agents.vehicles.BeamVehicle
import beam.agentsim.infrastructure.ChargingNetworkManager.PlanningTimeOutTrigger
import beam.agentsim.infrastructure.power.{PowerController, SitePowerManager}
import beam.agentsim.scheduler.BeamAgentScheduler.{CompletionNotice, ScheduleTrigger}
import beam.agentsim.scheduler.Trigger
import beam.agentsim.scheduler.Trigger.TriggerWithId
import beam.sim.config.BeamConfig
import beam.sim.{BeamScenario, BeamServices}
import beam.utils.DateUtils
import org.matsim.api.core.v01.Id

import scala.collection.concurrent.TrieMap

class ChargingNetworkManager(
  beamServices: BeamServices,
  beamScenario: BeamScenario
) extends Actor
    with ActorLogging {

  private val beamConfig: BeamConfig = beamScenario.beamConfig
  private val privateVehicles: TrieMap[Id[BeamVehicle], BeamVehicle] = beamScenario.privateVehicles

  private val sitePowerManager = new SitePowerManager()
  private val powerController = new PowerController(beamServices, beamConfig)
  private val vehiclesCopies: TrieMap[Id[BeamVehicle], BeamVehicle] = TrieMap.empty
  private val endOfSimulationTime: Int = DateUtils.getEndOfTime(beamConfig)

  private val gridConnectionEnabled = beamConfig.beam.agentsim.chargingNetworkManager.gridConnectionEnabled
  log.info(s"ChargingNetworkManager should be connected to grid: ${gridConnectionEnabled}")
  if (gridConnectionEnabled) {
    log.info(s"ChargingNetworkManager is connected to grid: ${powerController.isConnectedToGrid}")
  }

  override def receive: Receive = {
    case TriggerWithId(PlanningTimeOutTrigger(tick), triggerId) =>
      log.debug("PlanningTimeOutTrigger, tick: {}", tick)

      val requiredPower = sitePowerManager.getPowerOverPlanningHorizon(privateVehicles)

      val (bounds, nextTick) = if (gridConnectionEnabled) {
        powerController.publishPowerOverPlanningHorizon(requiredPower, tick)
        powerController.obtainPowerPhysicalBounds(tick)
      } else {
        powerController.defaultPowerPhysicalBounds(tick)
      }
      val requiredEnergyPerVehicle = sitePowerManager.replanHorizonAndGetChargingPlanPerVehicle(bounds, privateVehicles)

      log.info("Required energy per vehicle: {}", requiredEnergyPerVehicle.mkString(","))

      requiredEnergyPerVehicle.foreach {
        case (id, energy) if energy > 0 =>
          val vehicleCopy = vehiclesCopies.getOrElse(id, makeVehicleCopy(privateVehicles(id)))
          log.debug(
            "Charging vehicle {} (primaryFuelLevel = {}) with energy {}",
            vehicleCopy,
            vehicleCopy.primaryFuelLevelInJoules,
            energy
          )
          vehicleCopy.addFuel(energy)
          if (vehicleCopy.beamVehicleType.primaryFuelCapacityInJoule == vehicleCopy.primaryFuelLevelInJoules) {
            // vehicle is fully charged
            vehiclesCopies.remove(vehicleCopy.id)
          }
        case (id, energy) if energy < 0 =>
          log.warning(
            "Vehicle {}  (primaryFuelLevel = {}) requires negative energy {} - how could it be?",
            privateVehicles(id),
            privateVehicles(id).primaryFuelLevelInJoules,
            energy
          )
        case (id, 0) =>
          log.debug(
            "Vehicle {} is fully charged (primaryFuelLevel = {})",
            privateVehicles(id),
            privateVehicles(id).primaryFuelLevelInJoules
          )

      }

      log.debug("Copies of vehicles (dummy vehicles) after charging: {}", vehiclesCopies.mkString(","))

      sender ! CompletionNotice(
        triggerId,
        if (tick < endOfSimulationTime)
          Vector(ScheduleTrigger(PlanningTimeOutTrigger(nextTick), self))
        else
          Vector()
      )
  }

  override def postStop: Unit = {
    log.info("postStop")
    if (gridConnectionEnabled) {
      powerController.close()
    }
    super.postStop()
  }

  private def makeVehicleCopy(vehicle: BeamVehicle): BeamVehicle = {
    val vehicleCopy = new BeamVehicle(
      vehicle.id,
      vehicle.powerTrain,
      vehicle.beamVehicleType,
      vehicle.randomSeed
    )
    val initPrimaryFuel = -vehicle.beamVehicleType.primaryFuelCapacityInJoule + vehicle.primaryFuelLevelInJoules
    vehicleCopy.addFuel(initPrimaryFuel)
    vehicleCopy
  }
}

object ChargingNetworkManager {
  case class PlanningTimeOutTrigger(tick: Int) extends Trigger
}
