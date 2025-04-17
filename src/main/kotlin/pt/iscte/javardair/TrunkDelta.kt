package pt.iscte.javardair

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import model.FactoryOfTransformations
import model.transformations.Transformation
import model.uuid
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import pt.iscte.javardair.messages.ConflictInfo
import kotlin.concurrent.thread

data class TranformationConflict(
    val transformation: Transformation,
    val conflictInfo: ConflictInfo
)

object TrunkDelta {
    private val transformations: ObservableList<Transformation> = ObservableList()

    private val conflictsMap: ObservableConflictMap = ObservableConflictMap(mutableMapOf())

    fun addObserver(observer: (List<Transformation>) -> Unit) {
        transformations.addObserver(observer)
    }

    fun addConflictObserver(observer: ConflictsObserver) {
        conflictsMap.addObserver(observer)
    }

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

    fun updateConflicts(conflicts: MutableMap<String, Set<ConflictInfo>>) {
        conflicts.forEach { (client, conflictSet) ->
            conflictsMap[client] = conflictSet.toMutableList()
            conflictsMap.notifyObservers()
            println(conflictSet)
        }
    }

    fun isConflictFree(): Boolean {
        return conflictsMap.isEmpty()
    }

    fun hasConflict(transformation: Transformation): Boolean{
        return conflictsMap.values.any { it.any { it.conflictUUID == transformation.getNode().uuid.toString() } }
    }

    fun getConflictMessage(transformation: Transformation): String {
        return conflictsMap.values.find { it.find { it.conflictUUID == transformation.getNode().uuid.toString() } != null }
            ?.joinToString { it.conflictMessage }
            ?: ""
    }

    fun serializeTransformations(): JsonArray = JsonArray(transformations.map { it.toJson() })

    fun serializeTransformations(indexes: List<Int>): JsonArray = JsonArray(indexes.map { transformations[it].toJson() })

}