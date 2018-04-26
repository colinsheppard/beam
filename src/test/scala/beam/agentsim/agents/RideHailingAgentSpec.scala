package beam.agentsim.agents

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestActorRef, TestFSMRef, TestKit}
import akka.util.Timeout
import beam.agentsim.Resource.{CheckInResource, NotifyResourceIdle, RegisterResource}
import beam.agentsim.agents.BeamAgent.Finish
import beam.agentsim.agents.rideHail.RideHailingAgent
import beam.agentsim.agents.rideHail.RideHailingAgent._
import beam.agentsim.agents.vehicles.BeamVehicleType.Car
import beam.agentsim.agents.vehicles.EnergyEconomyAttributes.Powertrain
import beam.agentsim.agents.vehicles.{BeamVehicle, PassengerSchedule, VehiclePersonId}
import beam.agentsim.events.{PathTraversalEvent, SpaceTime}
import beam.agentsim.scheduler.BeamAgentScheduler.{CompletionNotice, ScheduleTrigger, SchedulerProps, StartSchedule}
import beam.agentsim.scheduler.{BeamAgentScheduler, Trigger, TriggerWithId}
import beam.router.Modes.BeamMode
import beam.router.RoutingModel.{BeamLeg, BeamPath}
import beam.router.r5.NetworkCoordinator
import beam.sim.BeamServices
import beam.sim.common.GeoUtilsImpl
import beam.sim.config.BeamConfig
import beam.utils.BeamConfigUtils
import com.typesafe.config.ConfigFactory
import org.matsim.api.core.v01.events._
import org.matsim.api.core.v01.population.Person
import org.matsim.api.core.v01.{Coord, Id}
import org.matsim.core.events.EventsManagerImpl
import org.matsim.core.events.handler.BasicEventHandler
import org.matsim.vehicles._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, FunSpecLike}

import scala.collection.concurrent.TrieMap

class RideHailingAgentSpec extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString(
  """
  akka.log-dead-letters = 10
  akka.actor.debug.fsm = true
  akka.loglevel = debug
  """).withFallback(BeamConfigUtils.parseFileSubstitutingInputDirectory("test/input/beamville/beam.conf").resolve()))) with FunSpecLike
  with BeforeAndAfterAll with MockitoSugar with ImplicitSender {

  private implicit val timeout = Timeout(60, TimeUnit.SECONDS)
  val config = BeamConfig(system.settings.config)
  val eventsManager = new EventsManagerImpl()
  eventsManager.addHandler(new BasicEventHandler {
    override def handleEvent(event: Event): Unit = {
      self ! event
    }
  })

  val vehicles = TrieMap[Id[Vehicle], BeamVehicle]()
  val personRefs = TrieMap[Id[Person], ActorRef]()

  val services: BeamServices = {
    val theServices = mock[BeamServices]
    when(theServices.beamConfig).thenReturn(config)
    when(theServices.vehicles).thenReturn(vehicles)
    when(theServices.personRefs).thenReturn(personRefs)
    val geo = new GeoUtilsImpl(theServices)
    when(theServices.geo).thenReturn(geo)
    theServices
  }

  case class TestTrigger(tick: Double) extends Trigger

  private val networkCoordinator = new NetworkCoordinator(config, VehicleUtils.createVehiclesContainer())
  networkCoordinator.loadNetwork()

  describe("A RideHailingAgent") {

    def moveTo30000(scheduler: ActorRef, rideHailingAgent: ActorRef) = {
      expectMsgType[RegisterResource]

      scheduler ! ScheduleTrigger(InitializeTrigger(0), rideHailingAgent)
      scheduler ! ScheduleTrigger(TestTrigger(28800), self)
      scheduler ! StartSchedule(0)
      expectMsgType[CheckInResource] // Idle agent is idle
      expectMsgType[PersonDepartureEvent] // Departs..
      expectMsgType[PersonEntersVehicleEvent] // ..enters vehicle

      var trigger = expectMsgType[TriggerWithId] // 28800
      scheduler ! ScheduleTrigger(TestTrigger(30000), self)
      val passengerSchedule = PassengerSchedule()
        .addLegs(Seq(
          BeamLeg(28800, BeamMode.CAR, 10000, BeamPath(Vector(), None, SpaceTime(0.0, 0.0, 28800), SpaceTime(0.0, 0.0, 38800), 10000)),
          BeamLeg(38800, BeamMode.CAR, 10000, BeamPath(Vector(), None, SpaceTime(0.0, 0.0, 38800), SpaceTime(0.0, 0.0, 48800), 10000))
        ))
        .addPassenger(VehiclePersonId(Id.createVehicleId(1), Id.createPersonId(1)), Seq(
          BeamLeg(38800, BeamMode.CAR, 10000, BeamPath(Vector(), None, SpaceTime(0.0, 0.0, 38800), SpaceTime(0.0, 0.0, 48800), 10000))
        ))
      personRefs.put(Id.createPersonId(1), self) // I will mock the passenger
      rideHailingAgent ! ModifyPassengerSchedule(passengerSchedule)
      val modifyPassengerScheduleAck = expectMsgType[ModifyPassengerScheduleAck]
      modifyPassengerScheduleAck.triggersToSchedule.foreach(scheduler ! _)
      expectMsgType[VehicleEntersTrafficEvent]
      scheduler ! CompletionNotice(trigger.triggerId)

      // FIXME: Oops, I get a VehicleLeavesTrafficEvent for 38800 even though I can still interrupt the agent..
      expectMsgType[VehicleLeavesTrafficEvent]

      expectMsgType[TriggerWithId] // 30000
    }

    // Hopefully deterministic test, where we mock a router and give the agent just one option for its trip.
    it("should drive around when I tell him to") {
      val vehicleType = new VehicleTypeImpl(Id.create(1, classOf[VehicleType]))
      val vehicleId = Id.createVehicleId(1)
      val vehicle = new VehicleImpl(vehicleId, vehicleType)
      val beamVehicle = new BeamVehicle(new Powertrain(0.0), vehicle, None, Car)
      beamVehicle.registerResource(self)
      vehicles.put(vehicleId, beamVehicle)

      val scheduler = TestActorRef[BeamAgentScheduler](SchedulerProps(config, stopTick = 64800.0, maxWindow = 10.0))

      val rideHailingAgent = TestFSMRef(new RideHailingAgent(Id.create("1", classOf[RideHailingAgent]), scheduler, beamVehicle, new Coord(0.0, 0.0), eventsManager, services, networkCoordinator.transportNetwork))

      var trigger = moveTo30000(scheduler, rideHailingAgent)

      // Now I want to interrupt the agent, and it will say that for any point in time after 28800,
      // I can tell it whatever I want. Even though it is already 30000 for me.

      rideHailingAgent ! Interrupt()
      val interruptedAt = expectMsgType[InterruptedAt]
      assert(interruptedAt.tick >= 28800)
      assert(interruptedAt.tick <  38800) // I know this agent hasn't picked up the passenger yet
      assert(rideHailingAgent.stateName == Interrupted)
      expectNoMsg()
      // Still, I tell it to resume
      rideHailingAgent ! Resume()
      scheduler ! ScheduleTrigger(TestTrigger(50000), self)
      scheduler ! CompletionNotice(trigger.triggerId)

      expectMsgType[NotifyResourceIdle]

      expectMsgType[PathTraversalEvent]
      expectMsgType[VehicleEntersTrafficEvent]
      expectMsgType[VehicleLeavesTrafficEvent]

      trigger = expectMsgType[TriggerWithId] // NotifyLegStartTrigger
      scheduler ! CompletionNotice(trigger.triggerId)

      expectMsgType[NotifyResourceIdle]
      expectMsgType[PathTraversalEvent]
      expectMsgType[CheckInResource]

      trigger = expectMsgType[TriggerWithId] // NotifyLegEndTrigger
      scheduler ! CompletionNotice(trigger.triggerId)

      trigger = expectMsgType[TriggerWithId] // 50000
      scheduler ! CompletionNotice(trigger.triggerId)

      rideHailingAgent ! Finish
    }

  }


  override def afterAll: Unit = {
    shutdown()
  }

}

