package fr.insalyon.pld.agile.controller.implementation

import fr.insalyon.pld.agile.model.RoundRequest
import java.io.File

class LoadedDeliveriesState : DefaultState<RoundRequest>() {

  override fun init(controller: Controller, element: RoundRequest) {
    println("Etat actuelle = LOADED_DELIVERIES_STATE")
    println("Round request was well loaded")
    controller.roundRequest = element
  }

  override fun loadRoundRequest(controller: Controller) {
    defaultLoadRoundRequestImpl(controller)
  }

  override fun loadRoundRequest(controller: Controller, file : File) {
    fileLoadRoundRequestImpl(controller, file)
  }

  override fun calculateRound(controller: Controller) {
    println("Calculate round was called")
    defaultCalculateRoundImpl(controller)
  }
}
