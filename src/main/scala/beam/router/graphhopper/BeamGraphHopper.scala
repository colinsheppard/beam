package beam.router.graphhopper

import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.routing.weighting.TurnCostProvider.NO_TURN_COST_PROVIDER
import com.graphhopper.routing.weighting.Weighting.INFINITE_U_TURN_COSTS
import com.graphhopper.routing.weighting.{DefaultTurnCostProvider, FastestWeighting, TurnCostProvider, Weighting}
import com.graphhopper.util.PMap
import com.graphhopper.util.Parameters.Routing
import org.matsim.api.core.v01.network.Link
import org.matsim.core.router.util.TravelTime

class BeamGraphHopper(links: Seq[Link], travelTime: Option[TravelTime]) extends GraphHopper {

  override def createWeighting(profile: Profile, hints: PMap, disableTurnCosts: Boolean): Weighting = {
    if (profile.getWeighting == BeamGraphHopper.weightingName) {
      createBeamWeighting(profile, hints, disableTurnCosts)
    } else {
      super.createWeighting(profile, hints, disableTurnCosts)
    }
  }

  private def createBeamWeighting(profile: Profile, requestHints: PMap, disableTurnCosts: Boolean) = {
    val hints = new PMap
    hints.putAll(profile.getHints)
    hints.putAll(requestHints)

    val encoder = getEncodingManager.getEncoder(profile.getVehicle)
    val turnCostProvider = if (profile.isTurnCosts && !disableTurnCosts) {
      if (!encoder.supportsTurnCosts) throw new IllegalArgumentException("Encoder " + encoder + " does not support turn costs")
      val uTurnCosts = hints.getInt(Routing.U_TURN_COSTS, INFINITE_U_TURN_COSTS)
      new DefaultTurnCostProvider(encoder, getGraphHopperStorage.getTurnCostStorage, uTurnCosts)
    } else {
      NO_TURN_COST_PROVIDER
    }

    val time = profile.getName.split(BeamGraphHopper.profilePrefix)(1)
    val wayId2TravelTime = travelTime.map{times=>
          links.map(l =>
            l.getId.toString.toLong -> times.getLinkTravelTime(l, time.toInt, null, null)).toMap
    }.getOrElse(Map.empty)

    new BeamWeighting(encoder, turnCostProvider, wayId2TravelTime)
  }
}

object BeamGraphHopper {
  val profilePrefix = "beam_car_hour_"
  val weightingName = "beam"
}
