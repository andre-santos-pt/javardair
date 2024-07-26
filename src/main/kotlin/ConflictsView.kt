import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

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

/**
class ConflictView : JFrame("Conflict Viewer"), ConflictsObserver {
    private val listModel = DefaultListModel<String>()
    private val list = JList(listModel).apply {
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val index = locationToIndex(e.point)
                    val selectedItem = listModel.getElementAt(index)
                    handleConflictClick(selectedItem)
                }
            }
        })
    }

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(600, 400)
        layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)

        add(JScrollPane(list))
        setLocationRelativeTo(null)
        showView()
    }

    private fun appendConflictList(conflictMap: Map<String, MutableList<ConflictInfo>>) {
        listModel.clear()
        for ((pair, conflicts) in conflictMap) {
            conflicts.forEach { conflict ->
                listModel.addElement("Pair: $pair, Message: ${conflict.conflictMessage}, UUID: ${conflict.conflictedNodeUUID}")
            }
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

    private fun handleConflictClick(item: String) {
        // TODO Aplicar a transformaçao
        println("Clicked on: $item")

    }
}
        **/