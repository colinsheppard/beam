package beam.integration

import java.io.File

import beam.sim.RunBeam
import beam.sim.config.ConfigModule
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.util.Try

/**
  * Created by fdariasm on 29/08/2017
  * 
  */

class TransitPriceSpec extends WordSpecLike with Matchers with RunBeam with BeforeAndAfterAll with IntegrationSpecCommon {

  class StartWithModeChoiceAndTransitPrice(modeChoice: String, transitPrice: Double) extends EventsFileHandlingCommon{
    lazy val configFileName = Some(s"${System.getenv("PWD")}/test/input/beamville/beam_50.conf")

    val beamConfig = {

      ConfigModule.ConfigFileName = configFileName

      ConfigModule.beamConfig.copy(
        beam = ConfigModule.beamConfig.beam.copy(
          agentsim = ConfigModule.beamConfig.beam.agentsim.copy(
            agents = ConfigModule.beamConfig.beam.agentsim.agents.copy(
              modalBehaviors = ConfigModule.beamConfig.beam.agentsim.agents.modalBehaviors.copy(
                modeChoiceClass = modeChoice
              )
            ), tuning = ConfigModule.beamConfig.beam.agentsim.tuning.copy(
              transitCapacity = 1.0, transitPrice = transitPrice
            )
          ), outputs = ConfigModule.beamConfig.beam.outputs.copy(
            eventsFileOutputFormats = "xml"
          )
        )
      )
    }

    val exec = Try(runBeamWithConfig(beamConfig, ConfigModule.matSimConfig))
    val file: File = getRouteFile(beamConfig.beam.outputs.outputDirectory , beamConfig.beam.outputs.eventsFileOutputFormats)
    val eventsReader: ReadEvents = getEventsReader(beamConfig)
    val listValueTagEventFile = eventsReader.getListTagsFrom(new File(file.getPath),"type=\"ModeChoice\"","mode")
    val groupedCount = listValueTagEventFile
      .groupBy(s => s)
      .map{case (k, v) => (k, v.size)}
  }

  "Running beam with modeChoice ModeChoiceDriveIfAvailable and increasing transitPrice value" must {
    "create more entries for mode choice transit as value increases" in{
      val inputTransitPrice = Seq(0.1, 1.0)
      val modeChoice = inputTransitPrice.map(tc => new StartWithModeChoiceAndTransitPrice("ModeChoiceMultinomialLogit", tc).groupedCount)

      val tc = modeChoice
        .map(_.get("transit"))
        .filter(_.isDefined)
        .map(_.get)

      val z1 = tc.drop(1)
      val z2 = tc.dropRight(1)
      val zip = z2 zip z1

      println("Transit")
      println(tc)
      println(z1)
      println(z2)
      println(zip)

      isOrdered(tc)((a, b) => a >= b) shouldBe true
    }
  }


}
