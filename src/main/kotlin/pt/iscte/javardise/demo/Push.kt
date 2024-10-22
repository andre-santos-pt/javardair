package pt.iscte.javardise.demo

import CentralizedList
import ObservableList
import TransformationsView
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import messages.ClientMessage
import messages.ClientOperations
import model.FactoryOfTransformations
import pt.iscte.javardise.Command
import pt.iscte.javardise.CommandKind
import pt.iscte.javardise.CommandStack
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import java.io.File
import java.util.*
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

class Push : Action {
    override val name: String
        get() = "Push"

    private val transformations: ObservableList = CentralizedList.transformations


    override fun init(editor: CodeEditor) {
        //updateTransformations()

        // fires event at every editing command
        val commandObserver = { cmd: Command, _: Boolean, _: CommandStack? ->
            //updateTransformations()
        }
        editor.addCommandObserver(commandObserver)

        val fileObserver = { _: File, event: FileEvent, unit: CompilationUnit? ->
            //updateTransformations()
        }
        editor.addFileObserver(fileObserver)


    }

    private fun updateTransformations() {
        /**thread {
            synchronized(transformations) {
                transformations.clear()
                val factoryOfTransformations = FactoryOfTransformations(Client.projectRoot, Client.projectLocal)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())
            }
        }**/
    }

    private fun showAlertWindow() {
        SwingUtilities.invokeLater {
            val optionPane = JOptionPane(
                "You cannot submit your changes due to conflicts.",
                JOptionPane.WARNING_MESSAGE
            )
            val dialog = optionPane.createDialog("Conflicts Detected!")
            dialog.isAlwaysOnTop = true
            dialog.isVisible = true
        }
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        // Sends the changes to the server with the goal to propagate it.
        if(Client.isConnected && Client.isConflictFree()) {
            println("Transformation on Push: ${transformations.list}")
            val serializedTransformations = JsonArray(transformations.map { it.toJson() })
            try {
                val message = ClientMessage(ClientOperations.PUSH, Json.encodeToString(serializedTransformations))
                println("Sending changes manually: $message")
                Client.write(Json.encodeToString(message))
                transformations.clear()

            } catch (ex: Exception) {
                println("Could not send message to Server ${ex.printStackTrace()}")
            }
        } else {
            showAlertWindow() // mudar aqui para isto so acontecer se so tiver conflitos
        }
    }
}