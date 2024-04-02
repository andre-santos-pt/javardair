package pt.iscte.javardise.demo

import Client
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import model.FactoryOfTransformations
import model.Project
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

class ListTransformations : Action {
    override val name: String
        get() = "Submit"

    private lateinit var projBase: Project
    private lateinit var projBranch: Project

    val transformations: MutableSet<Transformation> = mutableSetOf()

    override fun init(editor: CodeEditor) {
        val memoryTypeSolver = MemoryTypeSolver()
        projBase = Project(File(editor.folder, "base").absolutePath.toString())
        projBranch = Project(
            editor.folder.absolutePath.toString(),
            SymbolSolverCollectionStrategy().collect(
                Path(editor.folder.absolutePath)
            ),
            null,
            editor.allCompilationUnits().toMutableList(), // TODO new classes
            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
            memoryTypeSolver,
            true,
            true
        )

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
                val factoryOfTransformations = FactoryOfTransformations(projBase, projBranch)
                transformations.addAll(factoryOfTransformations.getListOfAllTransformations())
                println("transformations: $transformations")
            }
        }
    }

    // TODO so pode fazer isto se estiver ligado, proteger
    override fun run(editor: CodeEditor, toggle: Boolean) {

        val serializedTransformations = transformations.map { it.toJson().toString() }
        try {
            Client.writeMessage(serializedTransformations)
        } catch (ex: Exception) {
            println("Could not send message to Server")
        }
        //Client.write(serializedTransformations)

        /**try {
            serializedTransformations.map { json ->
                (Json.parseToJsonElement(json) as JsonObject).toTransformation(projBase)
            }.forEach {
                it.applyTransformation(projBase)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }**/


        //projBase.getSetOfCompilationUnit().forEach { println(it) }

        //Client.write(serializedTransformations) // ele aqui so faz a ligaçao uma vez? ou sempre que chamar o run ele tenta criar uma socket nova?

    }
}