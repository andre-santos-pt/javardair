import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JScrollPane
import javax.swing.JTextArea

// TODO tornas estas interfaces globais, porque uso algo muito igual no Transformation List
interface ConflictObservable {
    fun notifyObservers()
    fun addObserver(o: ConflictsObserver?)
}
interface ConflictsObserver {
    fun update(map: Map<String, MutableList<ConflictInfo>>)}

class ObservableConflictMap(val map: MutableMap<String, MutableList<ConflictInfo>>) : MutableMap<String, MutableList<ConflictInfo>> by map, ConflictObservable {
    private val observers: MutableSet<ConflictsObserver> = mutableSetOf()

    override fun put(key: String, value: MutableList<ConflictInfo>): MutableList<ConflictInfo>? {
        val result = map.put(key, value)
        notifyObservers()
        return result
    }

    override fun putAll(from: Map<out String, MutableList<ConflictInfo>>) {
        map.putAll(from)
        notifyObservers()
    }

    override fun remove(key: String): MutableList<ConflictInfo>? {
        val result = map.remove(key)
        notifyObservers()
        return result
    }

    override fun clear() {
        map.clear()
        notifyObservers()
    }

    override fun notifyObservers() {
        observers.forEach { it.update(this.map) }
    }

    override fun addObserver(o: ConflictsObserver?) {
        if (o != null) {
            observers.add(o)
        }
    }
}

class ConflictView : JFrame("Conflict Viewer"), ConflictsObserver {
    private val textArea = JTextArea()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(400, 300)
        layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)

        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true

        add(JScrollPane(textArea))
        setLocationRelativeTo(null)
        showView()
    }

    private fun appendConflictList(conflictMap: Map<String, MutableList<ConflictInfo>>) {
        textArea.text = ""
        for ((pair, conflicts) in conflictMap) {
            textArea.append("Pair: $pair\n")
            conflicts.forEach { conflict ->
                textArea.append("  Message: ${conflict.conflictMessage}\n")
                textArea.append("  UUID: ${conflict.conflictedNodeUUID}\n")
            }
            textArea.append("\n")
        }
    }

    fun showView() {
        isVisible = true
    }

    override fun update(map: Map<String, MutableList<ConflictInfo>>) {
        appendConflictList(map)
        revalidate()
        repaint()
    }
}
