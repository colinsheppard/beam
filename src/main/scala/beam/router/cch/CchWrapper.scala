package beam.router.cch

import beam.agentsim.agents.choice.mode.DrivingCost
import beam.agentsim.events.SpaceTime
import beam.cch.CchNative
import beam.router.BeamRouter.{RoutingRequest, RoutingResponse}
import beam.router.Modes.BeamMode
import beam.router.model.{BeamLeg, BeamPath, EmbodiedBeamLeg, EmbodiedBeamTrip}
import beam.router.r5.{CarWeightCalculator, R5Parameters}
import beam.router.{FreeFlowTravelTime, Router}
import beam.utils.FileUtils
import com.conveyal.osmlib.{Node, OSM, OSMEntity, Way}
import com.conveyal.r5.profile.StreetMode
import org.apache.commons.io
import org.matsim.core.router.util.TravelTime
import org.matsim.core.utils.misc.Time

import java.io.{File, FileOutputStream}
import java.nio.file.Paths
import scala.collection.JavaConverters._

class CchWrapper(workerParams: R5Parameters) extends Router {
  private val tempDir: File = Paths.get(System.getProperty("java.io.tmpdir"), "cchnative").toFile
  tempDir.mkdirs
  tempDir.deleteOnExit()

  if (System.getProperty("os.name").toLowerCase.contains("win")) {
    throw new IllegalStateException("Win is not supported")
  } else {
    val cchNativeLib = "libcchnative.so"
    io.FileUtils.copyInputStreamToFile(
      this.getClass.getClassLoader.getResourceAsStream(Paths.get("cchnative", cchNativeLib).toString),
      Paths.get(tempDir.toString, cchNativeLib).toFile
    )
    System.load(Paths.get(tempDir.getPath, cchNativeLib).toString)
  }

  private val noOfTimeBins = Math
    .floor(
      Time.parseTime(workerParams.beamConfig.beam.agentsim.endTime) /
      workerParams.beamConfig.beam.agentsim.timeBinSize
    )
    .toInt

  val carWeightCalculator = new CarWeightCalculator(workerParams)

  private var nativeCCH: CchNative = {
    nativeCCH = new CchNative()
    nativeCCH.init(prepareOsmFile())
    rebuildNativeCCHWeights(new FreeFlowTravelTime())
    nativeCCH
  }

  private def prepareOsmFile(): String = {
    val cchOsm = new OSM(null)
    val r5Osm = new OSM(workerParams.beamConfig.beam.routing.r5.osmMapdbFile)
    val vertexCur = workerParams.transportNetwork.streetLayer.vertexStore.getCursor

    def addNode(nodeId: Long): Node = {
      vertexCur.seek(nodeId.toInt)
      val node = new Node(vertexCur.getLat, vertexCur.getLon)
      cchOsm.nodes.put(nodeId, node)
    }

    val cur = workerParams.transportNetwork.streetLayer.edgeStore.getCursor
    for (idx <- 0 until workerParams.transportNetwork.streetLayer.edgeStore.nEdges by 1) {
      cur.seek(idx)

      if (cur.allowsStreetMode(StreetMode.CAR)) {
        val fromNodeId = cur.getFromVertex.toLong
        val toNodeId = cur.getToVertex.toLong
        if (!cchOsm.nodes.containsKey(fromNodeId)) {
          addNode(fromNodeId)
        }

        if (!cchOsm.nodes.containsKey(toNodeId)) {
          addNode(toNodeId)
        }

        val newWay = new Way()
        newWay.nodes = Array(fromNodeId, toNodeId)

        val osmWay = r5Osm.ways.get(cur.getOSMID)
        if (osmWay != null) {
          // TODO copy tags for each node?
          //
          //          if (r5Osm.nodes.containsKey(osmWay.nodes.head)) {
          //            val fromNode = r5Osm.nodes.get(osmWay.nodes.head)
          //            if (fromNode.tags != null) {
          //              fromNode.tags.forEach((tag: OSMEntity.Tag) => {
          //                cchOsm.nodes.get(fromNodeId).addTag(tag.key, tag.value)
          //              })
          //            }
          //          }
          //
          //          if (r5Osm.nodes.containsKey(osmWay.nodes.last)) {
          //            val toNode = r5Osm.nodes.get(osmWay.nodes.last)
          //            if (toNode.tags != null) {
          //              toNode.tags.forEach((tag: OSMEntity.Tag) => {
          //                cchOsm.nodes.get(toNodeId).addTag(tag.key, tag.value)
          //              })
          //            }
          //          }

          osmWay.tags.forEach((tag: OSMEntity.Tag) => {
            newWay.addOrReplaceTag(tag.key, tag.value)
          })
          newWay.addOrReplaceTag("oneway", "yes")
        } else {
          newWay.addOrReplaceTag("highway", "trunk")
          newWay.addOrReplaceTag("oneway", "yes")
        }

        cchOsm.ways.put(idx.toLong, newWay)
      }
    }
    val osmFile = Paths.get(tempDir.toString, "cch-generated.osm.pbf").toString
    FileUtils.using(new FileOutputStream(osmFile)) { fos =>
      cchOsm.writePbf(fos)
    }
    osmFile
  }

  override def calcRoute(req: RoutingRequest): RoutingResponse = {
    val origin = workerParams.geo.utm2Wgs(req.originUTM)
    val destination = workerParams.geo.utm2Wgs(req.destinationUTM)

    val bin = Math.floor(req.departureTime / workerParams.beamConfig.beam.agentsim.timeBinSize).toInt
    val cchResponse = nativeCCH.route(bin, origin.getX, origin.getY, destination.getX, destination.getY)
    val streetVehicle = req.streetVehicles.head
    val times = cchResponse.getTimes.asScala.map(_.toDouble)

    if (times.nonEmpty && cchResponse.getLinks.size() > 1 && cchResponse.getDepTime != 0L) {
      val beamTotalTravelTime = cchResponse.getDepTime.toInt - times.head.toInt
      val beamLeg = BeamLeg(
        req.departureTime,
        BeamMode.CAR,
        beamTotalTravelTime,
        BeamPath(
          cchResponse.getLinks.asScala.map(_.toInt).toIndexedSeq,
          times.toIndexedSeq,
          None,
          SpaceTime(origin, req.departureTime),
          SpaceTime(destination, req.departureTime + beamTotalTravelTime),
          cchResponse.getDistance
        )
      )
      val vehicleType = workerParams.vehicleTypes(streetVehicle.vehicleTypeId)
      val alternative = EmbodiedBeamTrip(
        IndexedSeq(
          EmbodiedBeamLeg(
            beamLeg,
            streetVehicle.id,
            streetVehicle.vehicleTypeId,
            asDriver = true,
            cost = DrivingCost.estimateDrivingCost(
              beamLeg.travelPath.distanceInM,
              beamLeg.duration,
              vehicleType,
              workerParams.fuelTypePrices(vehicleType.primaryFuelType)
            ),
            unbecomeDriverOnCompletion = true
          )
        )
      )

      RoutingResponse(Seq(alternative), req.requestId, Some(req), isEmbodyWithCurrentTravelTime = false)
    } else RoutingResponse(Seq(), req.requestId, Some(req), isEmbodyWithCurrentTravelTime = false)
  }

  def rebuildNativeCCHWeights(newTravelTime: TravelTime): Unit = {
    nativeCCH.lock()

    //    val futures = (0 until noOfTimeBins).map { bin =>
    (0 until noOfTimeBins).foreach { bin =>
      //      Future {
      val wayId2TravelTime =
        workerParams.networkHelper.allLinks.toSeq.map { l =>
          val linkId = l.getId.toString
          linkId -> carWeightCalculator
            .calcTravelTime(linkId.toInt, newTravelTime, bin * workerParams.beamConfig.beam.agentsim.timeBinSize)
            .toInt
            .toString
        }.toMap

      nativeCCH.createBinQueries(bin, wayId2TravelTime.asJava)
      //      }
    }
    //    Await.result(Future.sequence(futures), 20.minutes)
    nativeCCH.unlock()
  }
}