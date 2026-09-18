package pt.iscte.javardair.client

import model.FactoryOfTransformations
import model.transformations.Transformation
import model.uuid
import pt.iscte.javardair.server.ConflictInfo
import kotlin.concurrent.thread


object TrunkDelta {
    private val transformations = ObservableList<Transformation>()

    private val conflictsMap = ObservableMap<String, List<ConflictInfo>>()

    fun addObserver(observer: (List<Transformation>) -> Unit) {
        transformations.addObserver(observer)
    }

    fun removeObserver(observer: (List<Transformation>) -> Unit) {
        transformations.removeObserver(observer)
    }

    fun addConflictObserver(observer: (Map<String,List<ConflictInfo>>) -> Unit) {
        conflictsMap.addObserver(observer)
    }

    fun updateTransformations() {
        thread {
            synchronized(transformations) {
                transformations.clear()
                val factoryOfTransformations = FactoryOfTransformations(Client.projectTrunk, Client.projectLocal)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())
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

class ObservableList<T>(val list: MutableList<T> = mutableListOf()): MutableList<T> by list {
    private var observers: MutableSet<(List<T>) -> Unit> = mutableSetOf()

    override fun add(element: T): Boolean {
        val r = list.add(element)
        notifyObservers()
        return r
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val r = list.addAll(elements)
        notifyObservers()
        return r
    }

    override fun clear() {
        val r = list.clear()
        notifyObservers()
        return r
    }

    private fun notifyObservers() {
        observers.forEach {
            it(list)
        }
    }

    fun addObserver(o: (List<T>) -> Unit) {
        observers.add(o)
    }

    fun removeObserver(o: (List<T>) -> Unit) {
        observers.add(o)
    }
}


class ObservableMap<K,V>(val map: MutableMap<K, V> = mutableMapOf())
    : MutableMap<K,V> by map {
    private val observers: MutableSet<(Map<K,V>) -> Unit> = mutableSetOf()

    fun addObserver(o: (Map<K,V>) -> Unit) {
        observers.add(o)
    }

    fun notifyObservers() {
        observers.forEach {
            it(this)
        }
    }

    override fun put(key: K, value: V): V? {
        val result = map.put(key, value)
        notifyObservers()
        return result
    }

    override fun putAll(from: Map<out K,V>) {
        map.putAll(from)
        notifyObservers()
    }

    override fun remove(key: K): V? {
        val result = map.remove(key)
        notifyObservers()
        return result
    }

    override fun clear() {
        map.clear()
        notifyObservers()
    }

}