package pt.iscte.javardair.actions

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import model.setUUIDTo
import model.uuid
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Label
import pt.iscte.javardair.*
import pt.iscte.javardair.messages.ConflictInfo
import pt.iscte.javardise.*
import pt.iscte.javardise.basewidgets.ICodeDecoration
import pt.iscte.javardise.basewidgets.addMark
import pt.iscte.javardise.basewidgets.addNote
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import pt.iscte.javardise.external.findChild
import pt.iscte.javardise.external.getOrNull
import java.io.File
import java.util.*


class ConnectToServer : Action {

    override val name: String
        get() = "Connect"

    override val toggle: Boolean
        get() = true

//    override val toggleDefault: Boolean
//        get() = true

    override fun init(editor: CodeEditor) {
        val trans =  TrackChangesWindow(editor)
        TrunkDelta.addObserver {
            trans.updateTable(it)
        }
        trans.open()
        //updateTransformations()

        addConflictMarks(editor)

        // fires event at every editing command
        val commandObserver = { cmd: Command, _: Boolean, _: CommandStack? ->
            println("Command: ${cmd}")
            injectMemberUUIDs(cmd)
            TrunkDelta.updateTransformations()
        }
        editor.addCommandObserver(commandObserver)

        val fileObserver = { _: File, event: FileEvent, unit: CompilationUnit? ->
            if(event == FileEvent.CREATE && unit != null)
                injectClassUUIDs(unit)
            TrunkDelta.updateTransformations()
        }
        editor.addFileObserver(fileObserver)

//        editor.display.shells.firstOrNull()?.let {
//            Label(it, SWT.BORDER).text = "TEST"
//            it.requestLayout()
//        }

        // TODO add/remove file -> update project
        // TODO add file -> inject UUID
    }

    fun Node.getUuidFromComment(): String? {
        return comment.getOrNull?.let {
            when (it) {
                is LineComment -> it.content.trim()
                else -> null
            }
        }
    }

    private fun addConflictMarks(editor: CodeEditor) {
        TrunkDelta.addConflictObserver(object : ConflictsObserver {
            val marks = mutableListOf<ICodeDecoration<*>>()

            override fun update(map: Map<String, MutableList<ConflictInfo>>) {
                Display.getDefault().asyncExec {
                    marks.forEach { it.delete() }
                    marks.clear()
                    map.values.forEach { list ->
                        list.forEach { c ->
                            val uuid = c.conflictUUID
                            val control =
                                editor.classOnFocus?.findChild { (it.data as? Node)?.getUuidFromComment() == uuid }
                            println("Control: $control")
                            if (control != null) {
                                val m = control.addMark(
                                    Display.getDefault()
                                        .getSystemColor(SWT.COLOR_RED),
                                    c.conflictMessage
                                )
                                marks.add(m)
                                m.show()
                            }
                        }
                    }
                }
            }
        })
    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        if(!Client.isConnected && toggle) {
            Client.open(editor.folder, editor.allCompilationUnits())
        } else {
            Client.close()
        }
        editor.classOnFocus?.let {
            val m = it.getChildOnFocus()?.addNote(
                "TEST!!",
                ICodeDecoration.Location.TOP
            )
            m?.show()
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