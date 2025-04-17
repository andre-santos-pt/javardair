package pt.iscte.javardair.actions

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import model.setUUIDTo
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Label
import pt.iscte.javardair.CentralizedList
import pt.iscte.javardair.Client
import pt.iscte.javardair.TrackChangesWindow
import pt.iscte.javardise.Command
import pt.iscte.javardise.CommandKind
import pt.iscte.javardise.CommandStack
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import java.io.File
import java.util.*


class ConnectToServer : Action {

    override val name: String
        get() = "Connect"

    override val toggle: Boolean
        get() = true

    override fun init(editor: CodeEditor) {

//        val memoryTypeSolver = MemoryTypeSolver()
//
//        val trunkDir = File(editor.folder, trunkFolder)
//        if(!trunkDir.exists())
//            trunkDir.mkdirs()
//
//        Client.projectTrunk = Project(trunkDir.absolutePath)
//        Client.projectLocal = Project(
//            editor.folder.absolutePath.toString(),
//            SymbolSolverCollectionStrategy().collect(
//                Path(editor.folder.absolutePath)
//            ),
//            null,
//            editor.allCompilationUnits().toMutableList(),
//            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
//            memoryTypeSolver,
//            true,
//            true
//        )

        val trans =  TrackChangesWindow(editor)
        CentralizedList.addObserver(trans)
        trans.open()
        //updateTransformations()

        // fires event at every editing command
        val commandObserver = { cmd: Command, _: Boolean, _: CommandStack? ->
            injectMemberUUIDs(cmd)
            CentralizedList.updateTransformations()
        }
        editor.addCommandObserver(commandObserver)

        val fileObserver = { _: File, event: FileEvent, unit: CompilationUnit? ->
            if(event == FileEvent.CREATE && unit != null)
                injectClassUUIDs(unit)
            CentralizedList.updateTransformations()
        }
        editor.addFileObserver(fileObserver)

        editor.display.shells.firstOrNull()?.let {
            Label(it, SWT.BORDER).text = "TEST"
            it.requestLayout()
        }

        // TODO add/remove file -> update project
        // TODO add file -> inject UUID
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        if(!Client.isConnected && toggle) {
            Client.open(editor.folder, editor.allCompilationUnits())
        } else {
            Client.close()
        }
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
        if (cmd.kind == CommandKind.ADD && (cmd.element is MethodDeclaration || cmd.element is FieldDeclaration)) {
            val uuidAdded = UUID.randomUUID().toString()
            (cmd.element as BodyDeclaration<*>).setUUIDTo(model.UUID(uuidAdded))
        }
    }
}