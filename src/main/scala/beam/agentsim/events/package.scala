package beam.agentsim

import beam.agentsim.utils.MathUtils
import io.circe.{Encoder, Json}
import org.matsim.api.core.v01.Coord


/**
  * Convenience methods and classes on events
  *
  * Created by sfeygin on 4/5/17.
  */
package object events {

  case class SpaceTime(loc: Coord, time: Long)

  object SpaceTime {
    def apply(x: Double, y: Double, time: Long): SpaceTime = SpaceTime(new Coord(x, y), time)

    implicit  val encodeSpaceTime: Encoder[SpaceTime] = (a: SpaceTime) => {
      Json.fromValues(Seq[Json](
        Json.fromDoubleOrNull(MathUtils.roundDouble(a.loc.getX, 5)), // TODO: Hardcoded. Should this be configurable?
        Json.fromDoubleOrNull(MathUtils.roundDouble(a.loc.getY, 5)), // TODO: Ditto.
        Json.fromLong(a.time)))
    }
  }


}
