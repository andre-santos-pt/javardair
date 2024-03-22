package pt.iscte.javardise.demo

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import model.FactoryOfTransformations
import model.Project
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import kotlin.io.path.Path

class ListTransformations : Action {
    override val name: String
        get() = "Transformations"

    private lateinit var projBase: Project
    private lateinit var projBranch: Project

    override fun init(editor: CodeEditor) {
        val memoryTypeSolver = MemoryTypeSolver()
        projBase = Project(
            editor.folder.absolutePath.toString(),
            SymbolSolverCollectionStrategy().collect(
                Path(editor.folder.absolutePath)
            ),
            null,
            editor.allClasses().map { it.findCompilationUnit().get() }.toMutableList(),
            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
            memoryTypeSolver,
            true,
            true)
        projBranch = Project(File(editor.folder,".base").absolutePath.toString())
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        val factoryOfTransformations = FactoryOfTransformations(projBase, projBranch)
        val listOfTransformations = factoryOfTransformations.getListOfAllTransformations()
        println(listOfTransformations)
    }
}