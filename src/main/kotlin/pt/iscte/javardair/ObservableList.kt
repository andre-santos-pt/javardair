package pt.iscte.javardair


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