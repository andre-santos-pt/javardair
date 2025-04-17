package pt.iscte.javardair.actions

import pt.iscte.javardair.CentralizedList
import pt.iscte.javardair.ObservableList
import com.github.javaparser.ast.CompilationUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.eclipse.swt.widgets.Dialog
import org.eclipse.swt.widgets.Display
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

    // TODO to SWT
    private fun showAlertWindow(msg: String) {
        SwingUtilities.invokeLater {
            val optionPane = JOptionPane(
                msg,
                JOptionPane.WARNING_MESSAGE
            )
            val dialog = optionPane.createDialog("Push failed")
            dialog.isAlwaysOnTop = true
            dialog.isVisible = true
        }
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        if(!Client.isConnected)
            showAlertWindow("Not connected")
        else if(!Client.isConflictFree())
            showAlertWindow("There are conflicts")
        else {
            val serializedTransformations = JsonArray(transformations.map { it.toJson() })
            try {
                val message = ClientMessage(ClientOperations.PUSH, Json.encodeToString(serializedTransformations))
                Client.write(Json.encodeToString(message))
            } catch (ex: Exception) {
                println("Could not send message to Server ${ex.printStackTrace()}")
            }
        }
    }
}