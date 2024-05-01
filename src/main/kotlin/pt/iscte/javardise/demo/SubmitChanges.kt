package pt.iscte.javardise.demo

import Client
import Client.getPrivatePath
import Operations
import Message
import TransformationsView
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import model.FactoryOfTransformations
import model.applyTransformationsTo
import model.transformations.Transformation
import pt.iscte.javardise.Command
import pt.iscte.javardise.CommandKind
import pt.iscte.javardise.CommandStack
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import java.io.File
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path

class SubmitChanges : Action {
    override val name: String
        get() = "Submit"

    private val transformations: MutableSet<Transformation> = mutableSetOf()
    private val transformationsView: TransformationsView = TransformationsView(transformations)

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

        transformationsView.showView()

    }

    private fun injectClassUUIDs(unit: CompilationUnit) {
        if(!unit.comment.isPresent)
            unit.setComment(LineComment(UUID.randomUUID().toString()))

        unit.types.filter { !it.comment.isPresent }.forEach {
            it.setComment(LineComment(UUID.randomUUID().toString()))
        }
    }

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
                println("transformations: $transformations")
                transformationsView.updateTransformationList(transformations)
            }
        }
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        val serializedTransformations = JsonArray(transformations.map { it.toJson() })
        transformations.clear()
        try {
            val message = Message(Operations.PUSH, Json.encodeToString(serializedTransformations))
            println("Sending changes: $message")
            Client.write(Json.encodeToString(message))

        } catch (ex: Exception) {
            println("Could not send message to Server ${ex.printStackTrace()}")
        }
    }
}