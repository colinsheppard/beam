package beam.utils.beamToVia.appsForVisualizations

import beam.utils.beamToVia.IO.{EventsReader, Writer}
import beam.utils.beamToVia.beamEvent.BeamPathTraversal
import beam.utils.beamToVia.beamEventsFilter.{MutablePopulationFilter, MutableSamplingFilter, PopulationSample}
import beam.utils.beamToVia.viaEvent.ViaEvent

object visualization_20_24 extends App {
  val beamEventsFilePath = "D:/Work/BEAM/visualizations/v2.it20.events.bridge_cap_5000.half.csv"
  val sampleSize = 0.1

  val viaOutputBaseFilePath = "D:/Work/BEAM/visualizations/v20_24.it20.events.bridge_cap_5000.popSize" + sampleSize
  val viaEventsFile = viaOutputBaseFilePath + ".via.xml"
  val viaIdsFile = viaOutputBaseFilePath + ".ids.txt"
  val viaActivitiesFile = viaOutputBaseFilePath + ".act.txt"
  val viaModesFile = viaOutputBaseFilePath + ".mode.txt"

  def notADriver(id: String) = !id.contains("Agent")
  val filter: MutableSamplingFilter = MutablePopulationFilter(Seq(PopulationSample(sampleSize, notADriver)))

  def vehicleType(pte: BeamPathTraversal): String = pte.mode + "_" + pte.vehicleType
  def vehicleId(pte: BeamPathTraversal): String = vehicleType(pte) + "__" + pte.vehicleId

  val (vehiclesEvents, personsEvents) = EventsReader.readWithFilter(beamEventsFilePath, filter)
  val (events, typeToId) = EventsReader.transformPathTraversals(vehiclesEvents, vehicleId, vehicleType)

  val (modeChoiceEvents, modeToCnt) = EventsReader.transformModeChoices(personsEvents, 20)
  modeChoiceEvents.foreach(events.enqueue(_))

  val (activities, activityToCnt) = EventsReader.transformActivities(personsEvents)
  activities.foreach(events.enqueue(_))

  Writer.writeViaEventsQueue[ViaEvent](events, _.toXml.toString, viaEventsFile)

  Writer.writeViaActivities(activityToCnt, viaActivitiesFile)
  Writer.writeViaModes(modeToCnt, viaModesFile)
  Writer.writeViaIdFile(typeToId, viaIdsFile)
}
