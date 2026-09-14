package pt.iscte.javardair.actions

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import kotlinx.serialization.json.jsonPrimitive
import model.setUUIDTo
import org.eclipse.swt.SWT
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.events.MouseTrackListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*
import pt.iscte.javardair.*
import pt.iscte.javardair.messages.ConflictInfo
import pt.iscte.javardise.*
import pt.iscte.javardise.basewidgets.ICodeDecoration
import pt.iscte.javardise.basewidgets.addDecoration
import pt.iscte.javardise.basewidgets.addMark
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import pt.iscte.javardise.external.findChild
import pt.iscte.javardise.external.getOrNull
import pt.iscte.javardise.external.onClick
import java.io.File
import java.util.*


class ConnectToServer : Action {

    override val name: String
        get() = "Connect"

    override val toggle: Boolean
        get() = true

    override fun init(editor: CodeEditor) {
        ClientProperties.load(editor.folder.absolutePath)
        val trans = TrackChangesWindow(editor)
        TrunkDelta.addObserver {
            trans.updateTable(it)
        }
        TrunkDelta.addConflictObserver {
            trans.updateConflicts()
        }
        trans.open()

        addConflictMarks(editor)

        // fires event at every editing command
        val commandObserver = { cmd: Command, _: Boolean, _: CommandStack? ->
            injectMemberUUIDs(cmd)
            TrunkDelta.updateTransformations()
        }
        editor.addCommandObserver(commandObserver)

        val fileObserver =
            { _: File, event: FileEvent, unit: CompilationUnit? ->
                if (event == FileEvent.CREATE && unit != null)
                    injectClassUUIDs(unit)
                TrunkDelta.updateTransformations()
            }
        editor.addFileObserver(fileObserver)

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
        val marks = mutableListOf<ICodeDecoration<*>>()
        var popupShell: Shell? = null
        TrunkDelta.addConflictObserver {
            Display.getDefault().asyncExec {
                marks.forEach { it.delete() }
                marks.clear()
                it.values.forEach { list ->
                    list.forEach { c ->
                        val uuid = c.conflictUUID
                        val control =
                            editor.classOnFocus?.findChild { (it.data as? Node)?.getUuidFromComment() == uuid }
                        if (control != null) {
                            val m = control.addMark(
                                Display.getDefault()
                                    .getSystemColor(SWT.COLOR_RED),
                                c.conflictMessage
                            )
                            marks.add(m)
                            m.show()
                            m.control.onClick {
                                popupShell?.dispose()
                                popupShell = Shell(
                                    editor.display,
                                    SWT.ON_TOP or SWT.TOOL or SWT.NO_FOCUS or SWT.NO_TRIM
                                )
                                popupShell?.layout = RowLayout(SWT.VERTICAL)

                                Group(popupShell, SWT.NONE).apply {
                                    text =
                                        "Incompatibility with ${c.collaborator}"
                                    layout = RowLayout(SWT.VERTICAL)

                                    Label(this, SWT.NONE).apply {
                                        text = c.conflictMessage
                                    }

                                    Text(this, SWT.BORDER).apply {
                                        text = when(c.conflictingTransformation["code"]?.jsonPrimitive?.content) {
                                            "BodyChangedCallable" -> c.conflictingTransformation["body"]?.jsonPrimitive?.content
                                            "SignatureChanged" -> c.conflictingTransformation["name"]?.jsonPrimitive?.content
                                            else -> ""
                                        }
                                        editable = false
                                    }

                                    Composite(this, SWT.NONE).apply {
                                        layout = RowLayout(SWT.HORIZONTAL)
                                        Button(this, SWT.PUSH).apply {
                                            text = "Accept theirs"
                                            addSelectionListener(object :
                                                SelectionAdapter() {
                                                override fun widgetSelected(e: SelectionEvent) {
                                                    Client.acceptChanges(c)
                                                    popupShell?.dispose()
                                                }
                                            })
                                        }
                                        Button(this, SWT.PUSH).apply {
                                            text = "Close"
                                            addSelectionListener(object :
                                                SelectionAdapter() {
                                                override fun widgetSelected(e: SelectionEvent) {
                                                    popupShell?.dispose()
                                                }
                                            })
                                        }
                                    }
                                }

                                popupShell?.location =
                                    control.toDisplay(0, control.size.y)
                                popupShell?.pack()
                                popupShell?.open()
                                m.control.addDisposeListener {
                                    popupShell?.dispose()
                                }
                            }

                        }
                    }
                }
            }
        }
    }

    private fun ConflictInfo.isConflictSolvable() =
        conflictingTransformation["code"].toString().matches(Regex("BodyChangedCallable|SignatureChanged"))

    override fun run(editor: CodeEditor, toggle: Boolean) {
        if (!Client.isConnected && toggle) {
            Client.open(editor.folder, editor.allCompilationUnits())
        } else {
            Client.close()
        }
    }

    // TODO estas funcoes deviam estar noutro ficheiro nao?
    private fun injectClassUUIDs(unit: CompilationUnit) {
        if (!unit.comment.isPresent)
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