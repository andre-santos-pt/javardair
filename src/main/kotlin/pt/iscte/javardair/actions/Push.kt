package pt.iscte.javardair.actions

import pt.iscte.javardair.TrunkDelta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import pt.iscte.javardair.Client
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

class Push : Action {
    override val name: String
        get() = "Push"

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
            val serializedTransformations = TrunkDelta.serializeTransformations()
            try {
                val message = ClientMessage(ClientOperations.PUSH, Json.encodeToString(serializedTransformations))
                Client.write(Json.encodeToString(message))
            } catch (ex: Exception) {
                println("Could not send message to Server ${ex.printStackTrace()}")
            }
        }
    }
}