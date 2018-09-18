package beam.router

import akka.actor.{Actor, ActorLogging, Props}
import akka.routing.FromConfig
import beam.router.r5.R5RoutingWorker
import com.typesafe.config.Config

class ClusterWorkerRouter(config: Config) extends Actor with ActorLogging {

  // This router is used both with lookup and deploy of routees. If you
  // have a router with only lookup of routees you can use Props.empty
  // instead of Props[StatsWorker.class].

  val workerRouter = context.actorOf(
    FromConfig.props(Props(classOf[R5RoutingWorker], config)),
    name = "workerRouter"
  )
  def getNameAndHashCode: String = s"ClusterWorkerRouter[${hashCode()}], Path: `${self.path}`"
  log.info("{} inited. workerRouter => {}", getNameAndHashCode, workerRouter)

  def receive = {
    case other =>
      log.debug("{} received {}", getNameAndHashCode, other)
      workerRouter.forward(other)
  }
}
