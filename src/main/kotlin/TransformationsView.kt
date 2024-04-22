import model.transformations.Transformation
import java.awt.Dimension
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JTextArea

class TransformationsView(initialTransformation: MutableSet<Transformation>) : JFrame("Transformations") {
    private val textArea = JTextArea()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(300, 150)
        layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)

        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true

        appendTransformationList(initialTransformation)

        add(textArea)
        setLocationRelativeTo(null)
    }

    private fun appendTransformationList(transformation: MutableSet<Transformation>) {
        textArea.text = ""

        for (line in transformation) {
            textArea.append(line.getText())
            textArea.append("\n \n")
        }
    }

    fun updateTransformationList(newTransformationList: MutableSet<Transformation>) {
        appendTransformationList(newTransformationList)
        revalidate()
        repaint()
    }

    fun showView() {
        isVisible = true
    }
}