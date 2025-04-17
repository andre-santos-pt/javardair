package pt.iscte.javardair

import model.transformations.Transformation

interface Observable {
    fun notifyObservers()
    fun addObserver(o: Observer)
}

interface Observer {
    fun update(list: MutableSet<Transformation>)
}

class ObservableList(val list: MutableSet<Transformation>): MutableSet<Transformation> by list,
    Observable {
    private var observers: MutableSet<Observer> = mutableSetOf()
    override fun add(element: Transformation): Boolean {
        val r = list.add(element)
        notifyObservers()
        return r
    }

    override fun addAll(elements: Collection<Transformation>): Boolean {
        val r = list.addAll(elements)
        notifyObservers()
        return r
    }

    override fun clear() {
        val r = list.clear()
        notifyObservers()
        return r
    }

    override fun notifyObservers() {
        observers.forEach {
            it.update(this.list)
        }
    }

    override fun addObserver(o: Observer) {
        observers.add(o)
    }
}