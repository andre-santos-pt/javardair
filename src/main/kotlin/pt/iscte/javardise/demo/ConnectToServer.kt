package pt.iscte.javardise.demo

import Client
import model.Project
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor

class ConnectToServer : Action {
    override val name: String
        get() = "Connect"
    /**
    override fun init(editor: CodeEditor) {
        val memoryTypeSolver = MemoryTypeSolver()
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
    **/


    override fun run(editor: CodeEditor, toggle: Boolean) {
        if(!Client.isConnected) {
            Client.open()
        } else { Client.close() }

    }
}