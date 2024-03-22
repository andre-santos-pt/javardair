package pt.iscte.javardise.demo

import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import model.Project
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import kotlin.io.path.Path

class ApplyTransformations : Action {
    override val name: String
        get() = "Modify"

    private lateinit var projBase: Project

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
    }


    override fun run(editor: CodeEditor, toggle: Boolean) {
        val json = listOf("""
            {"code":"SignatureChanged","uuid":"d0779f95-d537-4501-b708-fc50747e6616","new-name":"newName","parameters":[{"type":"int","name":"param"}]}
        """)
        json.map {
            (Json.parseToJsonElement(it) as JsonObject).toTransformation(projBase)
        }.forEach {
            it.applyTransformation(projBase)
        }
    }
}