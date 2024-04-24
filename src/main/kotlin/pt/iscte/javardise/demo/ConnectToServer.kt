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

    override fun init(editor: CodeEditor) {
        val memoryTypeSolver = MemoryTypeSolver()
        Client.projectRoot = Project(File(editor.folder, "root").absolutePath.toString())
        Client.projectLocal = Project(
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
            Client.open()
        } else { Client.close() }

    }
}