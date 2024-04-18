package pt.iscte.javardise.demo

import Client
import Client.getPrivatePath
import Client.projectBranch
import Operations
import Message
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
                val factoryOfTransformations = FactoryOfTransformations(Client.projectBase, Client.projectBranch)
                //println(factoryOfTransformations)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())
                println("transformations: $transformations")
            }
        }
    }

    // TODO so pode fazer isto se estiver ligado, proteger
    override fun run(editor: CodeEditor, toggle: Boolean) {
        val serializedTransformations = JsonArray(transformations.map { it.toJson() })
        try {
            val message = Message(Operations.PUSH, Json.encodeToString(serializedTransformations))
            Client.write(Json.encodeToString(message))
            applyTransformationsTo(Client.projectBase, transformations)
            Client.projectBase.saveProjectTo(Path(Client.projectBase.getPrivatePath()))

        } catch (ex: Exception) {
            println("Could not send message to Server ${ex.printStackTrace()}")
        }
        transformations.clear()
    }
}