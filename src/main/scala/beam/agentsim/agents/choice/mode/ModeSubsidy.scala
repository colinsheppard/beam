package beam.agentsim.agents.choice.mode

import java.io.File

import beam.agentsim.agents.choice.mode.ModeSubsidy.Subsidy
import beam.router.Modes.BeamMode
import beam.sim.population.AttributesOfIndividual
import org.matsim.api.core.v01.Id
import org.matsim.vehicles.Vehicle

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.Try

case class ModeSubsidy(
  modeSubsidies: Map[BeamMode, List[Subsidy]],
  agencyAndRouteByVehicleIds: TrieMap[Id[Vehicle], (String, String)]
) {

  def computeSubsidy(
    attributesOfIndividual: AttributesOfIndividual,
    vehiclesInTrip: Seq[Id[Vehicle]],
    mode: BeamMode
  ): Double = {
    val transitSubsidies =
      if (vehiclesInTrip != null)
        agencyAndRouteByVehicleIds
          .filterKeys(vehiclesInTrip.contains)
          .values
          .map(
            v =>
              //subsidy for public transport(bus, train, transit) with agency id and route id
              getSubsidy(
                mode,
                attributesOfIndividual.age,
                attributesOfIndividual.income.map(x => x.toInt),
                Some(v._1),
                Some(v._2)
            )
          )
          .filter(_.isDefined)
          .toList
      else List()

    val subsidy: Double =
      if (transitSubsidies.nonEmpty)
        transitSubsidies.flatten.sum
      else {
        // subsidy for non-public transport
        getSubsidy(
          mode,
          attributesOfIndividual.age,
          attributesOfIndividual.income.map(x => x.toInt),
          None,
          None
        ).getOrElse(0)
      }
    subsidy
  }

  def getSubsidy(
    mode: BeamMode,
    age: Option[Int],
    income: Option[Int],
    agencyId: Option[String],
    routeId: Option[String]
  ): Option[Double] = {
    modeSubsidies
      .getOrElse(mode, List())
      .filter(
        s =>
          //age and the income should match
          age.fold(false)(s.age.hasOrEmpty) && income.fold(true)(s.income.hasOrEmpty) && (
            (agencyId == s.agencyId && routeId == s.routeId) || // agency  and route matches or
            (agencyId == s.agencyId && s.routeId.isEmpty) || // agency matches but route should empty(any) or
            (s.agencyId.isEmpty && s.routeId.isEmpty) // both agency and route are empty (anu)
        )
      )
      .map(_.amount)
      .reduceOption(_ + _)
  }

}

object ModeSubsidy {

  def loadSubsidies(subsidiesFile: String): Map[BeamMode, List[Subsidy]] = {
    val subsidies: ListBuffer[Subsidy] = ListBuffer()
    val lines = Try(Source.fromFile(new File(subsidiesFile).toString).getLines().toList.tail).getOrElse(List())
    for (line <- lines) {
      val row = line.split(",")

      if (row.length == 6) subsidies += Subsidy(row(0), row(1), row(2), row(3), row(4), row(5))
    }
    subsidies.toList.groupBy(_.mode)
  }

  case class Subsidy(
    mode: BeamMode,
    age: Range,
    income: Range,
    agencyId: Option[String],
    routeId: Option[String],
    amount: Double
  )

  object Subsidy {

    def apply(mode: String, age: String, income: String, agencyId: String, routeId: String, amount: String): Subsidy =
      new Subsidy(
        BeamMode.fromString(mode),
        Range(age),
        Range(income),
        if (null == agencyId || agencyId.trim.isEmpty) None else Some(agencyId),
        if (null == routeId || routeId.trim.isEmpty) None else Some(routeId),
        Try(amount.toDouble).getOrElse(0D)
      )
  }
}
