package pt.iscte.javardair.actions

import pt.iscte.javardair.CentralizedList
import pt.iscte.javardair.ObservableList
import com.github.javaparser.ast.CompilationUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import model.FactoryOfTransformations
import pt.iscte.javardair.Client
import pt.iscte.javardise.Command
import pt.iscte.javardise.CommandStack
import pt.iscte.javardair.toJson
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import java.io.File
import kotlin.concurrent.thread

class ForcePush : Action {
    override val name: String
        get() = "ForcePush"

    private val transformations: ObservableList = CentralizedList.transformations

    override fun run(editor: CodeEditor, toggle: Boolean) {
        // Sends the changes to the server with the goal to propagate it.
        if(Client.isConnected) {
            val serializedTransformations = JsonArray(transformations.map { it.toJson() })
            try {
                val message = ClientMessage(ClientOperations.FORCE_PUSH, Json.encodeToString(serializedTransformations))
                Client.write(Json.encodeToString(message))
                CentralizedList.updateTransformations()

            } catch (ex: Exception) {
                println("Could not send message to Server ${ex.printStackTrace()}")
            }
        } else {
            println("Not connected to the server.")
        }
    }
}