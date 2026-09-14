package pt.iscte.javardair

import model.FactoryOfTransformations
import model.transformations.Transformation
import model.uuid
import pt.iscte.javardair.messages.ConflictInfo
import kotlin.concurrent.thread


object TrunkDelta {
    private val transformations = ObservableList<Transformation>()

    private val conflictsMap = ObservableMap<String, List<ConflictInfo>>()

    fun addObserver(observer: (List<Transformation>) -> Unit) {
        transformations.addObserver(observer)
    }

    fun addConflictObserver(observer: (Map<String,List<ConflictInfo>>) -> Unit) {
        conflictsMap.addObserver(observer)
    }

    fun updateTransformations() {
        thread {
            synchronized(transformations) {
                transformations.clear()
                val factoryOfTransformations = FactoryOfTransformations(Client.projectTrunk, Client.projectLocal)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())  // é aqui que ele perde os comentarios
            }
        }
    }

    fun updateConflicts(conflicts: MutableMap<String, Set<ConflictInfo>>) {
        conflictsMap.clear()
        conflicts.forEach { (client, conflictSet) ->
            conflictsMap[client] = conflictSet.toMutableList()
            conflictsMap.notifyObservers()
        }
    }

    fun isConflictFree(): Boolean {
        println(conflictsMap)
        return conflictsMap.isEmpty()
    }

    fun getConflicts(transformation: Transformation): List<ConflictInfo> {
        return conflictsMap.values
            .flatten()
            .filter { it.conflictUUID == transformation.getNode().uuid.toString() }
    }

    fun hasConflict(transformation: Transformation): Boolean{
        return getConflicts(transformation).isNotEmpty()
    }

    fun getConflictMessage(transformation: Transformation): String {
        return getConflicts(transformation).joinToString { it.conflictMessage }
    }

    fun getConflictCollaborator(transformation: Transformation): String {
        return getConflicts(transformation).joinToString { it.collaborator }
    }
}