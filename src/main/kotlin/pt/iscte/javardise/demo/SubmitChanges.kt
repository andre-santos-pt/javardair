package pt.iscte.javardise.demo

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

class SubmitChanges : Action {
    override val name: String
        get() = "Push"

    private val transformations: ObservableList = ObservableList(mutableSetOf())
    private val transformationsView: TransformationsView = TransformationsView()



    override fun init(editor: CodeEditor) {
        updateTransformations()

        // fires event at every editing command
        val commandObserver = { cmd: Command, _: Boolean, _: CommandStack? ->
            injectMemberUUIDs(cmd)
            updateTransformations()
        }
        editor.addCommandObserver(commandObserver)

        val fileObserver = { _: File, event: FileEvent, unit: CompilationUnit? ->
            if(event == FileEvent.CREATE && unit != null)
                injectClassUUIDs(unit)
            updateTransformations()
        }
        editor.addFileObserver(fileObserver)

        transformations.addObserver(transformationsView)

    }

    // TODO estas funcoes deviam estar noutro ficheiro nao?
    private fun injectClassUUIDs(unit: CompilationUnit) {
        if(!unit.comment.isPresent)
            unit.setComment(LineComment(UUID.randomUUID().toString()))

        unit.types.filter { !it.comment.isPresent }.forEach {
            it.setComment(LineComment(UUID.randomUUID().toString()))
        }
    }

    // TODO estas funcoes deviam estar noutro ficheiro nao?
    private fun injectMemberUUIDs(cmd: Command) {
        if (cmd.kind == CommandKind.ADD &&
            (cmd.element is MethodDeclaration || cmd.element is FieldDeclaration))
            (cmd.element as BodyDeclaration<*>).setComment(
                LineComment(UUID.randomUUID().toString())
            )
    }


    private fun updateTransformations() {
        thread {
            synchronized(transformations) {
                transformations.clear()
                val factoryOfTransformations = FactoryOfTransformations(Client.projectRoot, Client.projectLocal)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())
                println("transformations: ${transformations.list}")

                // Sends the changes to server everytime a change is made.
                if(Client.isConnected) {
                    val serializedTransformations = JsonArray(transformations.map { it.toJson() })
                    try {
                        val message = ClientMessage(ClientOperations.UPDATE, Json.encodeToString(serializedTransformations))
                        println("Sending changes automatically: $message")
                        Client.write(Json.encodeToString(message))

                    } catch (ex: Exception) {
                        println("Could not send message to Server ${ex.printStackTrace()}")
                    }

                }

            }
        }
    }

    private fun showAlertWindow() {
        SwingUtilities.invokeLater {
            val optionPane = JOptionPane(
                "You cannot submit your changes due to conflicts.",
                JOptionPane.WARNING_MESSAGE
            )

            val dialog = optionPane.createDialog("Conflicts Detected!")
            dialog.isAlwaysOnTop = true  // Ensure the window stays in front
            dialog.isVisible = true
        }
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        // Sends the changes to the server with the goal to propagate it.
        if(Client.isConnected && Client.isConflictFree()) {
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
            showAlertWindow()
        }
    }
}