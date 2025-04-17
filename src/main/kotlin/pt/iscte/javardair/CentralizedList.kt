package pt.iscte.javardair

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import model.FactoryOfTransformations
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import kotlin.concurrent.thread

object CentralizedList {
    val transformations: ObservableList = ObservableList(mutableSetOf())

    fun updateTransformations() {
        thread {
            synchronized(transformations) {

                transformations.clear()
                val factoryOfTransformations = FactoryOfTransformations(Client.projectTrunk, Client.projectLocal)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())  // é aqui que ele perde os comentarios
                // Sends the changes to server everytime a change is made.
                if(Client.isConnected) {
                    val serializedTransformations = JsonArray(transformations.map { it.toJson() })
                    try {
                        val message = ClientMessage(ClientOperations.UPDATE, Json.encodeToString(serializedTransformations))
                        Client.write(Json.encodeToString(message))

                    } catch (ex: Exception) {
                        println("Could not send message to Server ${ex.printStackTrace()}")
                    }
                }
            }
        }
    }

    fun addObserver(observer: Observer) {
        transformations.addObserver(observer)
    }
}