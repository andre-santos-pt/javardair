package pt.iscte.javardair.client

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.comments.LineComment
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import com.github.javaparser.utils.SourceRoot
import kotlinx.serialization.json.jsonPrimitive
import model.Project
import model.UUID
import model.setUUIDTo
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*
import pt.iscte.javardair.server.ConflictInfo
import pt.iscte.javardise.*
import pt.iscte.javardise.basewidgets.ICodeDecoration
import pt.iscte.javardise.basewidgets.addMark
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.editor.FileEvent
import pt.iscte.javardise.external.findChild
import pt.iscte.javardise.external.getOrNull
import pt.iscte.javardise.external.onClick
import java.io.File
import java.io.PrintWriter
import kotlin.io.path.Path


class ConnectToServer : Action {

    override val name: String
        get() = "Connect"

    override val toggle: Boolean
        get() = true

    private lateinit var client: Client
    private lateinit var trunkDelta: TrunkDelta

    override fun init(editor: CodeEditor) {
        ClientProperties.load(editor.folder.absolutePath)

        val projectTrunk = Project(createTrunkDir(editor.folder).absolutePath)
        val projectLocal = loadProjectLocal(editor.folder.absolutePath, editor.allCompilationUnits())

        trunkDelta = TrunkDelta(projectTrunk, projectLocal)
        client = Client(projectTrunk, projectLocal, trunkDelta)

        TrackChangesView(editor, client, trunkDelta)
        addConflictMarks(editor)
        addObserverInjectUUIDsOnClassMembers(editor)
        addObserverInjectUUIDsOnFiles(editor)

        client.propagationEvent = { proj ->
            Display.getDefault().asyncExec {
                proj.getSetOfCompilationUnit().forEach {
                    editor.saveAndSyncRanges(
                        File(it.storage.get().path.toString()),
                        it
                    )
                }
            }
            trunkDelta.projectTrunk = proj
        }
        client.newFileEvent = {
            Display.getDefault().asyncExec {
                editor.openTab(it)
            }
        }
        client.errorHandler =  {
            Display.getDefault().asyncExec {
                MessageBox(
                    Shell(editor.display, SWT.NONE),
                    SWT.ICON_ERROR or SWT.OK
                ).apply {
                    text = it.title
                    this.message = it.message
                }.open()
            }
            it.exception?.printStackTrace()
        }
        client.connectionEvent = {
            // TODO button
        }

        trunkDelta.updateTransformations()
    }

    private fun createTrunkDir(rootPath: File): File {
        val trunkDir = File(rootPath, ClientProperties.TRUNK_FOLDER)
        if (!trunkDir.exists())
            trunkDir.mkdirs()
        return trunkDir
    }

    internal fun loadProjectLocal(path: String, compilationUnits: List<CompilationUnit>) : Project {
        val memoryTypeSolver = MemoryTypeSolver()
        return Project(
            path,
            SymbolSolverCollectionStrategy().collect(Path(path)),
            SourceRoot(Path(path)),
            compilationUnits.toMutableList(),
            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
            memoryTypeSolver,
            setupProject = true,
            initializeIndexes = true
        )
    }

    private fun addObserverInjectUUIDsOnClassMembers(editor: CodeEditor) {
        class IntectUUIDMembers(val cmd: Command) : Command {
            val uuid = java.util.UUID.randomUUID().toString()
            override val element: String
                get() = uuid
            override val kind: CommandKind
                get() = CommandKind.MODIFY
            override val target: Node
                get() = cmd.element as Node

            override fun run() {
                if (cmd.kind == CommandKind.ADD && (cmd.element is MethodDeclaration || cmd.element is FieldDeclaration))
                    (cmd.element as BodyDeclaration<*>).setUUIDTo(UUID(uuid))
            }

            override fun undo() {}
        }

        val commandObserver = { cmd: Command, _: Boolean, _: CommandStack? ->
            if (editor.classOnFocus != null && cmd !is IntectUUIDMembers) {
                // inject UUID on class members
                // command forces serialization
                editor.classOnFocus?.commandStack?.execute(IntectUUIDMembers(cmd))
                trunkDelta.updateTransformations()
            }
        }
        // fires event at every editing command
        editor.addCommandObserver(commandObserver)
    }

    private fun addObserverInjectUUIDsOnFiles(editor: CodeEditor) {
        fun injectClassUUIDs(unit: CompilationUnit) {

            if (!unit.packageDeclaration.isPresent)
                unit.setPackageDeclaration("todo")

            if (!unit.comment.isPresent)
                unit.setComment(
                    LineComment(
                        java.util.UUID.randomUUID().toString()
                    )
                )

            unit.types.filter { !it.comment.isPresent }.forEach {
                it.setComment(
                    LineComment(
                        java.util.UUID.randomUUID().toString()
                    )
                )
            }
        }
//        class IntectUUIDFile(val unit: CompilationUnit) : Command {
//            override val element: CompilationUnit
//                get() = unit
//            override val kind: CommandKind
//                get() = CommandKind.MODIFY
//            override val target: Node
//                get() = unit as Node
//
//            override fun run() {
//                injectClassUUIDs(unit)
//            }
//            override fun undo() { }
//        }

        fun writeFile(unit: CompilationUnit) {
            val w = PrintWriter(unit.storage.get().path.toString())
            w.write(unit.toString())
            w.close()
        }

        val fileObserver =
            { f: File, event: FileEvent, unit: CompilationUnit? ->
                if (event == FileEvent.CREATE && unit != null) {
//                    val w = (editor.allUnitWidgets().find { it.node == unit })
//                    println(w?.commandStack)
//                    w?.commandStack?.execute(IntectUUIDFile(unit))
                    injectClassUUIDs(unit)
                    client.addLocalJavaFile(unit)
                    //unit.setStorage(Path(f.absolutePath)) // TODO Jaid bug? storage is not set correctly?
                    writeFile(unit) // force serialization of changes
                    trunkDelta.updateTransformations()
                }

                // TODO event == FileEvent.DELETE
                // TODO event == FileEvent.RENAME
            }
        editor.addFileObserver(fileObserver)

    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        if (!client.isConnected && toggle)
            client.connect()
        else
            client.disconnect()
    }

    private fun addConflictMarks(editor: CodeEditor) {

        fun Node.getUuidFromComment(): String? {
            return comment.getOrNull?.let {
                when (it) {
                    is LineComment -> it.content.trim()
                    else -> null
                }
            }
        }

        val marks = mutableListOf<ICodeDecoration<*>>()
        var popupShell: Shell? = null
        trunkDelta.addConflictObserver {
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
                                        text =
                                            when (c.conflictingTransformation["code"]?.jsonPrimitive?.content) {
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
                                                    client.acceptChanges(c)
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
        conflictingTransformation["code"].toString()
            .matches(Regex("BodyChangedCallable|SignatureChanged"))
}