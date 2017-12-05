package fr.insalyon.pld.agile.service.roundmodifier.implementation

import fr.insalyon.pld.agile.model.*
import fr.insalyon.pld.agile.service.algorithm.implementation.Dijkstra
import fr.insalyon.pld.agile.service.roundmodifier.api.RoundModifier
import org.omg.CORBA.DynAnyPackage.InvalidValue

class RoundModifierImp(
    private var round: Round,
    private val plan: Plan
) : RoundModifier {

  override fun addDelivery(delivery: Delivery, round: Round) {
      if(isDeliveryValid(delivery)){

          var dijsktra: DijsktraImpl<Intersection, Junction> =  DijsktraImpl<Intersection, Junction>(plan, delivery.address)
          var prevShortestPath : Path<Intersection, Junction>? = null
          var nextShortestPath : Path<Intersection, Junction>? = null
          var listEarliestEndTime = getEarliestEndTime(round)
          var listLatestEndTime = getLastestEndTime(round)
          var listSubPath = mutableListOf<SubPath?>()

          round.deliveries().forEachIndexed { i, d ->

              if(i==0){
                  prevShortestPath = dijsktra.getShortestPath(round.warehouse.address)
                  nextShortestPath = dijsktra.getShortestPath(d.address)
              }else if(i>0){
                  prevShortestPath = dijsktra.getShortestPath(round.deliveries().elementAt(i-1).address)
                  nextShortestPath = dijsktra.getShortestPath(round.deliveries().elementAt(i).address)
              }

              if((getNextNonNull(listLatestEndTime, i)!= null && prevShortestPath!!.length + delivery.duration.toSeconds() + nextShortestPath!!.length + round.deliveries().elementAt(i+1).duration.length < (getNextNonNull(listLatestEndTime, i)!!.toSeconds() - listEarliestEndTime[i]!!.toSeconds()))
                       && ( delivery.startTime == null || (delivery.startTime != null && delivery.startTime!! < getNextNonNull(listLatestEndTime, i)!!))
                       && (delivery.startTime== null ||  (delivery.startTime != null && delivery.startTime!! < listEarliestEndTime.get(i)))) {
                  listSubPath.add(i, SubPath(prevShortestPath!!,prevShortestPath!!.toDuration(Config.defaultSpeed),delivery,nextShortestPath!!, nextShortestPath!!.toDuration(Config.defaultSpeed)))
              }else {
                  listSubPath.add(i, null)
              }
          }

          // we add the subpath between the potentialy added delivery and the warehouse to end the round
          listSubPath.add(listSubPath.size, SubPath(dijsktra.getShortestPath(round.deliveries().last().address),dijsktra.getShortestPath(round.deliveries().last().address).toDuration(15.km_h),delivery,dijsktra.getShortestPath(round.warehouse.address),dijsktra.getShortestPath(round.warehouse.address).toDuration(15.km_h)))

          var subPath : SubPath? = listSubPath.elementAt(0)
          var minLength : Long =0L

          if (subPath != null) {
              minLength = subPath.durationFromPreviousDelivery.toSeconds() + subPath.delivery.duration.toSeconds() + subPath.durationToNextDelivery.toSeconds()
          }

          listSubPath.forEach { sp ->
              if (sp != null) {
                  if(sp.durationFromPreviousDelivery.toSeconds() + sp.delivery.duration.toSeconds() + sp.durationToNextDelivery.toSeconds() < minLength){
                      minLength = sp.durationFromPreviousDelivery.toSeconds() + sp.delivery.duration.toSeconds() + sp.durationToNextDelivery.toSeconds()
                      subPath = sp
                  }
              }
          }

          if(subPath != null){
              round.addDelivery(subPath!!)
          } else {
              throw IllegalArgumentException("The delivery cannot be added to the round.")
          }


      }else{
          throw IllegalArgumentException("Check the values for the given delivery.")
      }
  }

  override fun removeDelivery(i: Int, round: Round, speed: Speed) {

    var dijsktra: Dijkstra<Intersection, Junction>
    if (i != 0) {
      dijsktra = Dijkstra<Intersection, Junction>(plan, round.deliveries().elementAt(i - 1).address)
    } else {
      dijsktra = Dijkstra<Intersection, Junction>(plan, round.warehouse.address)
    }

    val path: Path<Intersection, Junction>
    if (i != round.deliveries().size - 1) {
      path = dijsktra.getShortestPath(round.deliveries().elementAt(i + 1).address)
    } else {
      path = dijsktra.getShortestPath(round.warehouse.address)
    }
    val durationOfPath = path.toDuration(speed)
    round.removeDelivery(round.deliveries().elementAt(i), path, durationOfPath)
  }

  override fun modifyDelivery(delivery: Delivery, round: Round, i: Int) {
    val listEarliestEnd = getEarliestEndTime(round)
    val listLatestEnd = getLastestEndTime(round)

    var isFirstPartValid = true
    var isSecondPartValid = true

    if (isDeliveryValid(delivery)) {
      if (delivery.startTime != null && delivery.startTime < listEarliestEnd[i] + round.durationPathInSeconds().elementAt(i).length.seconds) {
        throw  IllegalArgumentException("The start time is not valid and must be greater than " + (listEarliestEnd[i] + round.durationPathInSeconds().elementAt(i).length.seconds).toFormattedString())
      }

      var nextNonNullLatestEndTime: Instant? = getNextNonNull(listLatestEnd, i)
      if (delivery.startTime != null && nextNonNullLatestEndTime != null && delivery.startTime!! + delivery.duration > nextNonNullLatestEndTime!! - round.deliveries().elementAt(i + 1).duration.length.seconds - round.durationPathInSeconds().elementAt(i + 1).length.seconds) {
        throw IllegalArgumentException("The sum of the start time and the duration cannot exceed " + (nextNonNullLatestEndTime!! - round.deliveries().elementAt(i + 1).duration.length.seconds
            - round.durationPathInSeconds().elementAt(i + 1).length.seconds))
      }

      if (delivery.endTime != null && listLatestEnd[i + 1] != null && i < round.deliveries().size - 1 && delivery.endTime > listLatestEnd[i + 1]!! - round.deliveries().elementAt(i + 1).duration.length.seconds
          - round.durationPathInSeconds().elementAt(i + 1).length.seconds) {

        throw IllegalArgumentException("The end time cannot exceed " + (listLatestEnd[i + 1]!! - round.deliveries().elementAt(i + 1).duration.length.seconds
            - round.durationPathInSeconds().elementAt(i + 1).length.seconds))
      }

      if (delivery.startTime == null && delivery.endTime == null) {
        nextNonNullLatestEndTime = getNextNonNull(listEarliestEnd, i)

        if (nextNonNullLatestEndTime != null && delivery.duration.toSeconds() > nextNonNullLatestEndTime!!.toSeconds() - listEarliestEnd[i].toSeconds()) {
          throw IllegalArgumentException("The duration cannot exceed " + (nextNonNullLatestEndTime.toSeconds() - listEarliestEnd[i].toSeconds()))
        }
      }

      round.modify(i, delivery.startTime, delivery.endTime, delivery.duration)
    } else {
      throw IllegalArgumentException("Check the values for the given delivery.")
    }
  }

  fun getLastestEndTime(round: Round): List<Instant?> {

    val result = mutableListOf<Instant?>()
    result.add(null)

    round.deliveries().reversed().forEachIndexed { i, delivery ->

      var index = round.deliveries().size - 1 - i
      if (result.first() == null) {
        result.add(0, delivery.endTime)
      } else {
        var lastestDeparture = result.first()!! - (round.deliveries().elementAt(index + 1).duration + round.durationPathInSeconds().elementAt(index + 1).length.seconds)
        if (delivery.endTime != null && delivery.endTime < lastestDeparture) result.add(0, delivery.endTime) else result.add(0, lastestDeparture)
      }
    }
    return result
  }

  fun getEarliestEndTime(round: Round): List<Instant> {

    val result = mutableListOf<Instant>()
    result += round.warehouse.departureHour

    round.deliveries().forEachIndexed { index, delivery ->
      val arrivalTime = result[index] + round.durationPathInSeconds().elementAt(index).length.seconds
      val startDelivery = if (delivery.startTime != null && delivery.startTime > arrivalTime) delivery.startTime else arrivalTime
      result += startDelivery + delivery.duration
    }

    val arrivalTime = result.last() + round.durationPathInSeconds().last().length.seconds
    result += arrivalTime

    return result

  }

  private fun getNextNonNull(list: List<Instant?>, i: Int): Instant? {
    var nextNonNullLatestEndTime: Instant? = null
    for (j: Int in i + 1..list.size) {
      if (j<list.size && list[j] != null) {
        nextNonNullLatestEndTime = list[j]
        break
      }
    }
    return nextNonNullLatestEndTime
  }

  /**
   * @param from : the delivery from the one you want to compute the time
   * @param to : the next delivery after from
   * @param round : the round you have to consider
   *
   * @return  the travelling time between the two given deliveries
   */
  private fun computeTravellingTime(from: Delivery, to: Delivery, round: Round): Int {
    var res = 0
    val index = round.deliveries().indexOf(to)
    return round.durationPathInSeconds().elementAt(index).toSeconds()
  }

  /**
   * @param from : the warehouse from which you want to compute the time
   * @param to : the next delivery after from
   * @param round : the round you have to consider
   *
   * @return  the travelling time between the two given deliveries
   */
  private fun computeTravellingTime(from: Warehouse, to: Delivery, round: Round): Int {
    var res = 0
    return round.durationPathInSeconds().first().toSeconds()
  }

  /**
   * @param from : the delivery from the one you want to compute the time
   * @param to : the warehouse you have to reach after from
   * @param round : the round you have to consider
   *
   * @return  the travelling time between the two given deliveries
   */
  private fun computeTravellingTime(from: Delivery, to: Warehouse, round: Round): Int {
    var res = 0
    return round.durationPathInSeconds().last().toSeconds()
  }

  private fun isDeliveryValid(delivery: Delivery): Boolean {
    if (delivery.startTime != null && delivery.endTime != null)
      if (delivery.startTime!! + delivery.duration > delivery.endTime) return false
    return true
  }

}

