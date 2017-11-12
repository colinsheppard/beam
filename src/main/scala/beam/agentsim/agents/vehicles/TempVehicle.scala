package beam.agentsim.agents.vehicles

import akka.actor.ActorRef
import beam.agentsim.agents.PersonAgent
import beam.agentsim.agents.vehicles.BeamVehicle.{BecomeDriverSuccessAck, DriverAlreadyAssigned, SetCarrier, VehicleCapacityExceeded}
import beam.agentsim.agents.vehicles.EnergyEconomyAttributes.Powertrain
import beam.agentsim.agents.vehicles.SeatAssignmentRule.RandomSeatAssignmentRule
import beam.agentsim.agents.vehicles.VehicleOccupancyAdministrator.DefaultVehicleOccupancyAdministrator
import org.apache.log4j.Logger
import org.matsim.api.core.v01.Id
import org.matsim.vehicles.{Vehicle, VehicleType}

import scala.util.Random

abstract case class TempVehicle(managerRef: ActorRef) extends Vehicle {
  val logger: Logger = Logger.getLogger("BeamVehicle")
  /**
    * Identifier for this vehicle
    */
  val id: Id[Vehicle]

  /**
    * MATSim vehicle delegate container (should be instantiated with all properties at creation).
    */
  implicit val matSimVehicle: Vehicle

  /**
    * Vehicle power train data
    *
    * @todo This information should be partially dependent on other variables contained in VehicleType
    */
  val powertrain: Powertrain

  /**
    * Manages the functionality to add or remove passengers from the vehicle according
    * to standing or sitting seating occupancy information.
    */
  val vehicleOccupancyAdministrator: VehicleOccupancyAdministrator = DefaultVehicleOccupancyAdministrator()

  /**
    * The [[beam.agentsim.ResourceManager]] who is currently managing this vehicle. Must
    * not ever be None ([[Vehicle]]s start out with a manager even if no driver is initially assigned.
    * There is usually only ever one manager for a vehicle.
    *
    * @todo consider adding owner as an attribute of the vehicle as well, since this is somewhat distinct
    *       from driving... (SAF 11/17)
    */
  var manager: Option[ActorRef] = Option(managerRef)

  /**
    * The [[PersonAgent]] who is currently driving the vehicle (or None ==> it is idle).
    * Effectively, this is the main controller of the vehicle in space and time in the scenario environment;
    * whereas, the manager is ultimately responsible for assignment and (for now) ownership
    * of the vehicle as a physical property.
    */
  var driver: Option[ActorRef] = None

  /**
    * The vehicle that is carrying this one. Like ferry or truck may carry a car and like a car carries a human body.
    */
  var carrier: Option[ActorRef] = None

  /**
    * The list of passenger vehicles (e.g., people, AVs, cars) currently occupying the vehicle.
    */
  var passengers: Set[Id[Vehicle]] = Set()

  override def getType: VehicleType = matSimVehicle.getType

  override def getId: Id[Vehicle] = id

  def setManager(managerRef: ActorRef): Unit = {
    manager = Option(managerRef)
  }

  /**
    * Called by the driver.
    */
  def unsetDriver(): Unit = {
    driver = None
  }

  /**
    * Only permitted if no driver is currently set. Driver has full autonomy in vehicle, so only
    * a call of [[unsetDriver]] will remove the driver.
    * Send back appropriate response to caller depending on protocol.
    *
    * @param newDriverRef incoming driver
    */
  def setDriver(newDriverRef: ActorRef): Either[DriverAlreadyAssigned, BecomeDriverSuccessAck] = {

    if (driver.isEmpty) {
      driver = Option(newDriverRef)
      Right(BecomeDriverSuccessAck(id))
    }
    else {
      Left(DriverAlreadyAssigned(id, driver.get))
    }
  }


}

object TempVehicle {
  def energyPerUnitByType(vehicleTypeId: Id[VehicleType]): Double = {
    //TODO: add energy type registry
    0.0
  }

  def noSpecialChars(theString: String): String = theString.replaceAll("[\\\\|\\\\^]+", ":")


}


abstract case class VehicleOccupancyAdministrator(implicit vehicle: TempVehicle) {

  val seatedOccupancyLimit: Int = vehicle.getType.getCapacity.getSeats
  val standingOccupancyLimit: Int = vehicle.getType.getCapacity.getStandingRoom
  val totalOccupancyLimit: Int = seatedOccupancyLimit + standingOccupancyLimit

  var seatedPassengers: Set[Id[Vehicle]] = Set()
  var standingPassengers: Set[Id[Vehicle]] = Set()

  implicit val seatAssignmentRule: SeatAssignmentRule

  def getSeatsRemaining: Int = seatedOccupancyLimit - seatedPassengers.size

  def getStandingRoomRemaining: Int = standingOccupancyLimit - standingPassengers.size

  def getTotalRoomRemaining: Int = getSeatsRemaining + getStandingRoomRemaining

  def getSeatedCrowdedness: Double = (seatedPassengers.size / totalOccupancyLimit).toDouble

  def getStandingCrowdedness: Double = (standingPassengers.size / totalOccupancyLimit).toDouble

  def getTotalCrowdedness: Double = ((standingPassengers.size + seatedPassengers.size) / totalOccupancyLimit).toDouble

  def addSeatedPassenger(id: Id[Vehicle]): Either[VehicleCapacityExceeded, SetCarrier] = {
    if (seatedPassengers.size + 1 > seatedOccupancyLimit) {
      Left(VehicleCapacityExceeded(id))
    } else {
      seatedPassengers += id
      Right(SetCarrier(id))
    }
  }

  def addStandingPassenger(id: Id[Vehicle]): Either[VehicleCapacityExceeded, SetCarrier] = {
    if (standingPassengers.size + 1 > standingOccupancyLimit) {
      Left(VehicleCapacityExceeded(id))
    } else {
      standingPassengers += id
      Right(SetCarrier(id))
    }
  }

  def addPassenger(id: Id[Vehicle]): Either[VehicleCapacityExceeded, SetCarrier] = {
    if (seatAssignmentRule.assignSeat(id, standingPassengers, seatedPassengers)) {
      addSeatedPassenger(id)
    } else {
      addStandingPassenger(id)
    }
  }

}

object VehicleOccupancyAdministrator {

  case class DefaultVehicleOccupancyAdministrator() extends VehicleOccupancyAdministrator {
    override val seatAssignmentRule: SeatAssignmentRule = implicitly[RandomSeatAssignmentRule]
  }

}

trait SeatAssignmentRule {
  def assignSeat(id: Id[Vehicle], standingPassengers: Set[Id[Vehicle]], seatedPassengers: Set[Id[Vehicle]])(implicit vehicle: Vehicle): Boolean
}

object SeatAssignmentRule {

  class RandomSeatAssignmentRule extends SeatAssignmentRule {
    override def assignSeat(id: Id[Vehicle], standingPassengers: Set[Id[Vehicle]], seatedPassengers: Set[Id[Vehicle]])(implicit vehicle: Vehicle): Boolean = Random.nextBoolean()
  }

}


//
//case class VehicleStack(nestedVehicles: Vector[Id[Vehicle]] = Vector()){
//  def isEmpty = nestedVehicles.isEmpty
//
//  def pushIfNew(vehicle: Id[Vehicle]) = {
//    if (!nestedVehicles.isEmpty && nestedVehicles.head == vehicle) {
//      VehicleStack(nestedVehicles)
//    } else {
//      VehicleStack(vehicle +: nestedVehicles)
//    }
//  }
//
//  def penultimateVehicle(): Id[Vehicle] = {
//    if (nestedVehicles.size < 2) throw new RuntimeException("Attempted to access penultimate vehilce when 1 or 0 are in the vehicle stack.")
//    nestedVehicles(1)
//  }
//
//  def outermostVehicle(): Id[Vehicle] = {
//    nestedVehicles(0)
//  }
//  def pop(): VehicleStack = {
//    VehicleStack(nestedVehicles.tail)
//  }
//}
