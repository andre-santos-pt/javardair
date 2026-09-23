package pt.iscte.javardair.client

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.comments.LineComment
import kotlinx.serialization.json.jsonPrimitive
import model.transformations.*
import org.checkerframework.checker.units.qual.m
import org.eclipse.swt.SWT
import org.eclipse.swt.events.MouseAdapter
import org.eclipse.swt.events.MouseEvent
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.widgets.*
import org.eclipse.swt.widgets.Event
import pt.iscte.javardair.JsonPretty
import pt.iscte.javardair.getPrivateField
import pt.iscte.javardair.server.ConflictInfo
import pt.iscte.javardair.toJson
import pt.iscte.javardair.toTransformation
import pt.iscte.javardise.basewidgets.ICodeDecoration
import pt.iscte.javardise.basewidgets.addMark
import pt.iscte.javardise.basewidgets.addMark3
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.external.findChild
import pt.iscte.javardise.external.getOrNull
import pt.iscte.javardise.external.onClick
import pt.iscte.javardise.findChild
import java.io.File
import kotlin.reflect.KClass




class TrackChangesView(val editor: CodeEditor, val client: Client, val trunkDelta: TrunkDelta) {

    private val icons = listOf("plus","minus","rename","edit").associateWith { loadIcon(it) }

    private fun loadIcon(name: String) = TrackChangesView::class.java.getClassLoader()
        .getResourceAsStream("icons${File.separator}$name.png")?.let {
            Image(Display.getDefault(), it)
        }

    val changesView = editor.createExtraComposite()
    val table = Table(
        changesView,
        SWT.BORDER or SWT.V_SCROLL or SWT.H_SCROLL or SWT.MULTI
    )


    init {
        editor.display.shells.first().text =
            "${ClientProperties.clientName}: ${editor.folder}"
        changesView.layout = FillLayout()
        changesView.layoutData = GridData(SWT.FILL, SWT.FILL, true, false)
        table.headerVisible = true
        table.linesVisible = true

        addDebugView()

        TableColumn(table, SWT.NONE).apply {
            text = "Transformation"
            width = 250
        }
        TableColumn(table, SWT.NONE).apply {
            text = "Confliting"
            width = 100
        }
        TableColumn(table, SWT.NONE).apply {
            text = "Reason"
            width = 250
        }

        createPopupMenu()

        trunkDelta.addObserver {
            updateTable(it)
        }
        trunkDelta.addConflictObserver {
            updateConflicts()
        }

        addConflictObserver()
    }

    private fun addDebugView() {
        if (ClientProperties.debug) {
            val debugView = editor.createExtraComposite().apply {
                layout = FillLayout()
                Text(this, SWT.MULTI or SWT.V_SCROLL or SWT.H_SCROLL).apply {
                    text = "debug"
                    layoutData = GridData(SWT.FILL, SWT.FILL, true, true).apply {
                        minimumHeight = 300;
                    }
                }
            }.children.first() as Text
            table.addSelectionListener(object : SelectionAdapter() {
                override fun widgetSelected(e: SelectionEvent) {
                    if (table.selection.isNotEmpty()) {
                        val json =
                            (table.selection[0].data as Transformation).toJson(
                                client.projectLocal)
                                .toString()
                        debugView.text = JsonPretty.print(json)
                        debugView.requestLayout()
                    } else {
                        debugView.text = "debug"
                        debugView.requestLayout()
                    }
                }
            })
        }
    }

    private fun createPopupMenu() {
        val popup = Menu(table)
        val propagate = MenuItem(popup, SWT.NONE)
        propagate.text = "Propagate"
        propagate.addListener(SWT.Selection) { e ->
            val selectedSet = table.selection
                .map { it.data   as Transformation }
            try {
                client.propagate(selectedSet)

            } catch (ex: Exception) {
                Display.getDefault().asyncExec {
                    MessageBox(
                        Display.getDefault().activeShell,
                        SWT.ICON_ERROR or SWT.OK
                    ).apply {
                        text = "Error"
                        message = ex.message
                    }.open()
                }
            }
        }

        val accept = MenuItem(popup, SWT.NONE).apply {
            text = "Accept theirs"
            enabled = false
            addListener(SWT.Selection) { e ->
                val conflicts =
                    trunkDelta.getConflicts(table.selection.first().data as Transformation)
                conflicts.forEach {
                    client.acceptChanges(it)
                }

            }
        }

        val rollback = MenuItem(popup, SWT.NONE)
        rollback.text = "Rollback"
        rollback.enabled = false
        rollback.addListener(SWT.Selection) { e ->

        }

        table.menu = popup

        table.addListener(SWT.MenuDetect) { e: Event? ->
            val selection = table.selection
            if (selection.isEmpty()) {
                propagate.enabled = false
                rollback.enabled = false
            } else {
                val selectedSet = table.selection
                    .map { it.data as Transformation }

                propagate.enabled =
                    client.isConnected && selectedSet.isNotEmpty() && selectedSet.none {
                        trunkDelta.hasConflict(it)
                    }
                accept.enabled = selectedSet.size == 1 && trunkDelta.getConflicts(selectedSet.first()).isNotEmpty()
                //rollback.enabled = true // TODO: check if rollback is possible
            }
        }

        table.addMouseListener(object: MouseAdapter() {
            override fun mouseDoubleClick(e: MouseEvent) {
               if(table.selection.size == 1) {
                   val t = table.selection.first().data as Transformation
                   if(t is AddFile) {
                       //editor.openTab(t.getNode()) // TODO Javardise bug
                   }
                   else
                        editor.classOnFocus?.findChild(t.getNode())?.setFocus()
               }
            }
        })
//        table.addSelectionListener(object : SelectionAdapter() {
//            var mark: ICodeDecoration<*>? = null
//            override fun widgetSelected(e: SelectionEvent) {
//                mark?.hide()
//                if(table.selection.size == 1) {
//                    val t = table.selection.first().data as Transformation
//                    mark = editor.classOnFocus?.findChild(t.getNode())
//                        ?.addMark3(Display.getDefault().getSystemColor(SWT.COLOR_GRAY))
//                    mark?.show()
//                }
//            }
//        })
    }

    private fun updateTable(list: List<Transformation>) {
        Display.getDefault().asyncExec {
            val selected =
                table.items.filter { it.checked }.map { it.getText(0) }
            table.items.forEach { it.dispose() }
            for (t in list) {
                val item = TableItem(table, SWT.NONE)
                item.checked = selected.contains(t.message())
                item.setImage(0, t.icon())
                item.setText(
                    arrayOf(
                        t.message(),
                        trunkDelta.getConflictCollaborator(t),
                        trunkDelta.getConflicts(t).joinToString { it.conflictMessage }
                    )
                )
                if (trunkDelta.hasConflict(t))
                    item.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_RED)

                item.data = t
            }
            table.requestLayout()
        }
    }

    private fun updateConflicts() {
        Display.getDefault().asyncExec {
            table.items.forEach {
                val transformation = it.data as Transformation
                val conflicts = trunkDelta.getConflicts(transformation)
                if (conflicts.isNotEmpty()) {
                    it.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_RED)
                    it.setText(1, conflicts.joinToString { it.collaborator })
                    it.setText(2, conflicts.joinToString { it.conflictMessage })
                } else {
                    it.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_BLACK)
                    it.setText(1, "")
                    it.setText(2, "")
                }
            }
        }
    }

    private fun Transformation.icon(): Image? =
        if(this::class.simpleName?.startsWith("Add") == true)
            icons["plus"]
        else if(this::class.simpleName?.startsWith("Remove") == true)
            icons["minus"]
        else if(this::class.simpleName?.startsWith("Rename") == true || this is SignatureChanged)
            icons["rename"]
        else
            icons["edit"]


    private fun Transformation.message(): String {
        fun SignatureChanged.sig() = getParentNode().nameAsString + "." + getNode().asString()
        return when (this) {
            is SignatureChanged -> if (nameChanged())
                "${sig()} renamed to ${getParentNode().nameAsString + "." + getNewName() + "()"}"
            else if (parametersChanged())
                "${sig()} parameters changed to (${getNewParameters().joinToString { it.typeAsString }})"
            else
                getText()

            is AddField -> {
                getParentNode().nameAsString + "." + getNode().variables.first().nameAsString + " field added"
            }

            is RemoveField -> {
                getParentNode().nameAsString + "." + getNode().variables.first().nameAsString + " field removed"
            }
            is AddCallable -> {
                getParentNode().nameAsString + "." + getNode().nameAsString + "(...) method added"
            }

            is RemoveCallable -> {
                getParentNode().nameAsString + "." + getNode().nameAsString + "(...) method removed"
            }

            is BodyChangedCallable -> {
                val type =
                    this.getPrivateField("type") as TypeDeclaration<*>
                val callable =
                    this.getPrivateField("callable") as CallableDeclaration<*>
                type.nameAsString + "." + callable.asString() + " edited"
            }

            is AddFile -> this.getNode().storage.getOrNull?.fileName?.let {
                "$it added"
            } ?: getText()

            is RemoveFile -> this.getNode().storage.getOrNull?.fileName?.let {
                "$it removed"
            } ?: getText()

            else -> getText()
        }
    }

    private fun CallableDeclaration<*>.asString(): String {
        return when (this) {
            is ConstructorDeclaration ->
                "contructor(${parameters.joinToString { it.typeAsString }})"

            is MethodDeclaration ->
                "${nameAsString}(${parameters.joinToString { it.typeAsString }})"

            else -> toString()
        }
    }


//    private fun ConflictInfo.message(): String {
//        val localTrans = this.transformation.toTransformation(client.projectLocal)
//        val conflictTrans =  this.conflictingTransformation.toTransformation(client.projectLocal)
//        return if(localTrans is SignatureChanged && conflictTrans is SignatureChanged) {
//            if(localTrans.getNewName() != conflictTrans.getNewName())
//                "Different renames"
//            else
//                "Different signatures"
//        }
//        else if(localTrans is BodyChangedCallable && conflictTrans is BodyChangedCallable)
//            "Different method body"
//        else
//            this.conflictMessage
//    }

    private fun addConflictObserver() {

        fun Node.getUuidFromComment(): String? {
            return comment.getOrNull?.let {
                when (it) {
                    is LineComment -> it.content.trim()
                    else -> null
                }
            }
        }

        val marks = mutableListOf<ICodeDecoration<*>>()
        val controlConflict: MutableMap<Control, ConflictInfo> = mutableMapOf()

        Display.getDefault().addFilter(SWT.MouseDown) {
            if(it.widget is Control && controlConflict.containsKey(it.widget)) {
                val control = it.widget as Control
                val c = controlConflict[control]!!

                val popupShell = Shell(
                    editor.display,
                    SWT.SYSTEM_MODAL or SWT.TOOL or SWT.NO_FOCUS or SWT.NO_TRIM
                )
                popupShell.layout = RowLayout(SWT.VERTICAL)

                Group(popupShell, SWT.NONE).apply {
                    text =
                        "Conflicting with ${c.collaborator}"
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
                                    popupShell.dispose()
                                }
                            })
                        }
                        Button(this, SWT.PUSH).apply {
                            text = "Close"
                            addSelectionListener(object :
                                SelectionAdapter() {
                                override fun widgetSelected(e: SelectionEvent) {
                                    popupShell.dispose()
                                }
                            })
                        }
                    }
                }
                popupShell.location =
                    control.toDisplay(0, control.size.y)
                popupShell.pack()
                popupShell.open()
            }

        }
        trunkDelta.addConflictObserver {
            Display.getDefault().asyncExec {
                marks.forEach { it.delete() }
                marks.clear()
                controlConflict.clear()
                it.values.forEach { list ->
                    list.forEach { c ->
                        val uuid = c.conflictUUID
                        val control =
                            editor.classOnFocus?.findChild { (it.data as? Node)?.getUuidFromComment() == uuid }
                        if (control != null) {
                            val m = control.addMark3(
                                Display.getDefault()
                                    .getSystemColor(SWT.COLOR_RED),
                                c.conflictMessage
                            )
                            marks.add(m)
                            m.show()
                            controlConflict[control] = c
                        }
                    }
                }
            }
        }
    }
}





