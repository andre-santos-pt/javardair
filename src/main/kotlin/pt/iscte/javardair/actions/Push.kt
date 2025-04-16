package pt.iscte.javardair.actions

import pt.iscte.javardair.CentralizedList
import pt.iscte.javardair.ObservableList
import com.github.javaparser.ast.CompilationUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import pt.iscte.javardair.Client
import pt.iscte.javardise.Command
import pt.iscte.javardise.CommandStack
import pt.iscte.javardair.toJson
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

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
            val serializedTransformations = JsonArray(transformations.map { it.toJson() })
            try {
                val message = ClientMessage(ClientOperations.PUSH, Json.encodeToString(serializedTransformations))
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