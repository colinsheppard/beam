package beam.agentsim.scheduler

import java.util.concurrent.TimeUnit

import akka.actor.ActorRef
import beam.agentsim.scheduler.Trigger.TriggerWithId
import beam.utils.Statistics
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/*
  This is dumb implementation. We store all the data to build percentiles. There are different approaches to get percentile on stream
  More: https://blog.superfeedr.com/streaming-percentiles/
 */
class TriggerMeasurer extends LazyLogging {
  private val triggerWithIdToStartTime: mutable.Map[TriggerWithId, Long] =
    mutable.Map[TriggerWithId, Long]()
  private val triggerTypeToOccurrence: mutable.Map[Class[_], ArrayBuffer[Long]] =
    mutable.Map[Class[_], ArrayBuffer[Long]]()
  private val actorToNumOfTriggerMessages: mutable.Map[ActorRef, Int] =
    mutable.Map[ActorRef, Int]()

  def sent(t: TriggerWithId, actor: ActorRef): Unit = {
    triggerWithIdToStartTime.put(t, System.nanoTime())

    val current = actorToNumOfTriggerMessages.get(actor).getOrElse(0)
    actorToNumOfTriggerMessages.update(actor, current + 1)
  }

  def resolved(t: TriggerWithId): Unit = {
    triggerWithIdToStartTime.get(t) match {
      case Some(startTime) =>
        val stopTime = System.nanoTime()
        val diff = TimeUnit.NANOSECONDS.toMillis(stopTime - startTime)
        val triggerClass = t.trigger.getClass
        triggerTypeToOccurrence.get(triggerClass) match {
          case Some(buffer) =>
            buffer.append(diff)
          case None =>
            val buffer = ArrayBuffer[Long](diff)
            triggerTypeToOccurrence.put(triggerClass, buffer)
        }

      case None =>
        logger.error(s"Can't find $t in triggerWithIdToStartTime")
    }
  }

  def getStat: String = {
    val sb = new mutable.StringBuilder()
    val nl = System.lineSeparator()
    triggerTypeToOccurrence.foreach {
      case (clazz, buf) =>
        val s = Statistics(buf.map(_.toDouble))
        sb.append(s"${nl}Type: $clazz${nl}Stats: $s$nl".stripMargin)
    }

    sb.append(s"${nl}Max number of trigger messages per actor type")
    // Do not remove `toIterable` (Map can't contain duplicates!)
    val actorTypeToCount: Iterable[(String, Int)] = actorToNumOfTriggerMessages.toIterable.map { case (actorRef, count) =>
      getType(actorRef) -> count
    }
    val maxPerActorType = actorTypeToCount.groupBy { case (actorType, _) => actorType }
      .map { case (actorType, seq) => actorType -> seq.view.map { case (_, count) => count}.max }

    maxPerActorType.foreach { case (actorType, total) =>
      sb.append(s"${nl}\t${actorType} => ${total}")
    }
    sb.toString()
  }

  private def getType(actorRef: ActorRef): String = {
    if (actorRef.path.parent.name == "router" && actorRef.path.name.indexOf("TransitDriverAgent-") != -1) {
//      val idx = actorRef.path.name.indexOf("TransitDriverAgent-")
//      val vehicleTypeAndOther = actorRef.path.name.substring(idx + "TransitDriverAgent-".length)
      "TransitDriverAgent"
    }
    else if (actorRef.path.parent.parent.name == "population") {
      "Population"
    }
    else if (actorRef.path.name.contains("rideHailAgent-")) {
      "RideHailAgent"
    }
    else if (actorRef.path.name == "RideHailManager") {
      "RideHailManager"
    }
    else {
      actorRef.path.toString
    }
  }
}
