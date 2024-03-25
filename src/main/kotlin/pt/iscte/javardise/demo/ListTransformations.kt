package pt.iscte.javardise.demo

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import model.FactoryOfTransformations
import model.Project
import model.transformations.Transformation
import pt.iscte.javardise.Command
import pt.iscte.javardise.CommandStack
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import kotlin.concurrent.thread
import kotlin.io.path.Path

class ListTransformations : Action {
    override val name: String
        get() = "Push Transformations"

    private lateinit var projBase: Project
    private lateinit var projBranch: Project

    val transformations: MutableSet<Transformation> = mutableSetOf()

    override fun init(editor: CodeEditor) {
        val memoryTypeSolver = MemoryTypeSolver()
        projBase = Project(File(editor.folder, ".base").absolutePath.toString())
        projBranch = Project(
            editor.folder.absolutePath.toString(),
            SymbolSolverCollectionStrategy().collect(
                Path(editor.folder.absolutePath)
            ),
            null,
            editor.allClasses().map { it.findCompilationUnit().get() }.toMutableList(),
            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
            memoryTypeSolver,
            true,
            true
        )

        updateTransformations()
        // fires event at every editing command
        val commandObserver = { _: Command?, _: Boolean?, _: CommandStack? ->
            updateTransformations()
        }
        editor.addCommandObserver(commandObserver)
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

    override fun run(editor: CodeEditor, toggle: Boolean) {
        println("propagate transformations... TODO")
        transformations.map { it.toJson() }.forEach { println(it) }
    }
}