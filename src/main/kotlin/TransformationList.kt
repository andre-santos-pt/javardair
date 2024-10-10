import model.transformations.Transformation
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JTextArea
import kotlin.concurrent.thread

interface Observable {
    fun notifyObservers()
    fun addObserver(o: Observer?)
}

interface Observer {
    fun update(list: MutableSet<Transformation>)
}

class ObservableList(val list: MutableSet<Transformation>): MutableSet<Transformation> by list, Observable {
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

    override fun addObserver(o: Observer?) {
        if (o != null) {
            observers.add(o)
        }
    }

}

class TransformationsView() : JFrame("Transformations"), Observer {
    private val textArea = JTextArea()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(300, 150)
        layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)

        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true

        //appendTransformationList()

        add(textArea)
        setLocationRelativeTo(null)

        showView()
    }

    private fun appendTransformationList(transformation: MutableSet<Transformation>) {
        textArea.text = ""
        for (line in transformation) {
            textArea.append(line.getText())
            textArea.append("\n \n")
        }
    }

    fun showView() {
        isVisible = true
    }

    override fun update(list: MutableSet<Transformation>) {
        appendTransformationList(list)
        revalidate()
        repaint()
    }
}