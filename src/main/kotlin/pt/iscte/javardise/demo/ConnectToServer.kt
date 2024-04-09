package pt.iscte.javardise.demo

import Client
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import model.Project
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import kotlin.io.path.Path

class ConnectToServer : Action {
    override val name: String
        get() = "Connect"

    private lateinit var projBranch: Project
    private lateinit var projBase: Project

    override fun init(editor: CodeEditor) {
        val memoryTypeSolver = MemoryTypeSolver()
        projBase = Project(File(editor.folder, "base").absolutePath.toString())
        projBranch = Project(
            editor.folder.absolutePath.toString(),
            SymbolSolverCollectionStrategy().collect(
                Path(editor.folder.absolutePath)
            ),
            null,
            editor.allCompilationUnits().toMutableList(),
            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
            memoryTypeSolver,
            true,
            true
        )
    }



    override fun run(editor: CodeEditor, toggle: Boolean) {
        if(!Client.isConnected) {
            Client.open(projBranch, projBase)
        } else { Client.close() }

    }
}