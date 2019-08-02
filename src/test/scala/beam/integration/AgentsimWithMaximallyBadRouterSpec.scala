package beam.integration

import akka.actor.Status.Failure
import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKitBase}
import beam.agentsim.agents.PersonTestUtil
import beam.agentsim.agents.ridehail.RideHailIterationHistory
import beam.agentsim.agents.ridehail.surgepricing.AdaptiveRideHailSurgePricingManager
import beam.integration.AgentsimWithMaximallyBadRouterSpec.BadRouterForTest
import beam.router.Modes.BeamMode
import beam.router.{BeamSkimmer, RouteHistory, TravelTimeObserved}
import beam.sim.common.GeoUtilsImpl
import beam.sim.{BeamHelper, BeamMobsim}
import beam.utils.SimRunnerForTest
import beam.utils.TestConfigUtils.testConfig
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.language.postfixOps

class AgentsimWithMaximallyBadRouterSpec
    extends WordSpecLike
    with TestKitBase
    with SimRunnerForTest
    with BadRouterForTest
    with BeamHelper
    with Matchers {

  def config: com.typesafe.config.Config =
    ConfigFactory
      .parseString("""akka.test.timefactor = 10
          |akka.loglevel = off
        """.stripMargin)
      .withFallback(testConfig("test/input/beamville/beam.conf").resolve())

  def outputDirPath: String = basePath + "/" + testOutputDir + "bad-router-test"

  lazy implicit val system: ActorSystem = ActorSystem("AgentSimWithBadRouterSpec", config)

  "The agentsim" must {
    "not get stuck even if the router only throws exceptions" in {
      scenario.getPopulation.getPersons.values
        .forEach(p => PersonTestUtil.putDefaultBeamAttributes(p, BeamMode.allModes))

      val mobsim = new BeamMobsim(
        services,
        beamScenario,
        beamScenario.transportNetwork,
        services.tollCalculator,
        scenario,
        services.matsimServices.getEvents,
        system,
        new AdaptiveRideHailSurgePricingManager(services),
        new RideHailIterationHistory(),
        new RouteHistory(services.beamConfig),
        new BeamSkimmer(beamScenario, services.geo),
        new TravelTimeObserved(beamScenario, services.geo),
        new GeoUtilsImpl(services.beamConfig),
        services.networkHelper
      )
      mobsim.run()
    }
  }
}

object AgentsimWithMaximallyBadRouterSpec {

  trait BadRouterForTest extends BeforeAndAfterAll with ImplicitSender {
    this: Suite with SimRunnerForTest with TestKitBase =>

    var router: ActorRef = _

    override def beforeAll: Unit = {
      super.beforeAll()
      router = TestActorRef(Props(new Actor {
        override def receive: Receive = {
          case _ =>
            sender ! Failure(new RuntimeException("No idea how to route."))
        }
      }))
      services.beamRouter = router
    }

    override def afterAll(): Unit = {
      router ! PoisonPill
      super.afterAll()
    }

  }
}
