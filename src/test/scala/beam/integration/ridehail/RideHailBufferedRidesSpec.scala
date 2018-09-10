package beam.integration.ridehail

import beam.integration.EventsFileHandlingCommon
import beam.sim.BeamHelper
import beam.utils.TestConfigUtils.testConfig
import com.typesafe.config.ConfigValueFactory
import org.matsim.api.core.v01.events.Event
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar

import scala.collection.immutable.Queue
import scala.collection.mutable.ArrayBuffer

class RideHailBufferedRidesSpec extends FlatSpec with BeamHelper with MockitoSugar with EventsFileHandlingCommon {

  def getActivitiesGroupedByPerson(events: Queue[Event]): Map[String, (ArrayBuffer[Event], ArrayBuffer[Event])] = {
    val activities = events.filter(e => "actstart".equals(e.getEventType) || "actend".equals(e.getEventType))

    val groupedByPerson = activities.foldLeft(Map[String, ArrayBuffer[Event]]()) {
      case (c, ev) =>
        val personId = ev.getAttributes.get("person")
        val array = c.getOrElse(personId, ArrayBuffer[Event]())
        array.append(ev)
        c.updated(personId, array)
    }

    groupedByPerson.map {
      case (id, events) =>
        val (startActEvents, endActEvents) =
          events.partition(e => "actstart".equals(e.getEventType))
        (id, (startActEvents, endActEvents))
    }

  }

  it should "have same actstart as endstart events for persons when using ridehail replacement in DummyRideHailDispatchWithBufferingRequests" ignore {
    val config = testConfig("test/input/beamville/beam.conf")
      .withValue("beam.outputs.events.fileOutputFormats", ConfigValueFactory.fromAnyRef("xml,csv"))
      .withValue(
        "beam.agentsim.agents.rideHail.allocationManager.name",
        ConfigValueFactory.fromAnyRef(
          "beam.agentsim.agents.rideHail.allocation.examples.DummyRideHailDispatchWithBufferingRequests"
          //"DEFAULT_MANAGER"
        )
      )
      .resolve()

    val matsimConfig = runBeamWithConfig(config)._1
    val filePath = getEventsFilePath(matsimConfig, "xml").getAbsolutePath
    val events = collectEvents(filePath)

    val groupedByPersonStartEndEvents = getActivitiesGroupedByPerson(events)

    assert(groupedByPersonStartEndEvents.forall {
      case (_, (startActEvents, endActEvent)) =>
        startActEvents.size == endActEvent.size
    })

//    groupedByPersonStartEndEvents.foreach{ case (_, (startActEvents, endActEvent)) =>
//      assert(startActEvents.size == endActEvent.size)
//    }

  }

  it should "have different actstart and endstart events for persons when NOT using ridehail replacement in DummyRideHailDispatchWithBufferingRequestsWithoutReplacement" ignore {
    val config = testConfig("test/input/beamville/beam.conf")
      .withValue("beam.outputs.events.fileOutputFormats", ConfigValueFactory.fromAnyRef("xml,csv"))
      .withValue(
        "beam.agentsim.agents.rideHail.allocationManager.name",
        ConfigValueFactory.fromAnyRef(
          "beam.agentsim.agents.ridehail.allocation.examples.DummyRideHailDispatchWithBufferingRequestsWithoutReplacement"
          //"DEFAULT_MANAGER"
        )
      )
      .resolve()

    val matsimConfig = runBeamWithConfig(config)._1
    val filePath = getEventsFilePath(matsimConfig, "xml").getAbsolutePath
    val events = collectEvents(filePath)

    val groupedByPersonStartEndEvents = getActivitiesGroupedByPerson(events)

    assert(!groupedByPersonStartEndEvents.forall {
      case (_, (startActEvents, endActEvent)) =>
        startActEvents.size == endActEvent.size
    })

  }

}
