package pt.iscte.javardair.actions

import pt.iscte.javardair.TrunkDelta
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import pt.iscte.javardair.Client
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor

class ForcePush : Action {
    override val name: String
        get() = "ForcePush"


    override fun run(editor: CodeEditor, toggle: Boolean) {
        // Sends the changes to the server with the goal to propagate it.
//        if(Client.isConnected) {
//            val serializedTransformations = TrunkDelta.serializeTransformations()
//            try {
//                val message = ClientMessage(ClientOperations.FORCE_PUSH, Json.encodeToString(serializedTransformations))
//                Client.write(Json.encodeToString(message))
//                TrunkDelta.updateTransformations()
//
//            } catch (ex: Exception) {
//                println("Could not send message to Server ${ex.printStackTrace()}")
//            }
//        } else {
//            println("Not connected to the server.")
//        }
    }
}