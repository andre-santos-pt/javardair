package pt.iscte.javardair

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import model.transformations.*
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ControlAdapter
import org.eclipse.swt.events.ControlEvent
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.RowData
import org.eclipse.swt.widgets.*
import org.eclipse.swt.widgets.Event
import pt.iscte.javardair.messages.ConflictInfo
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import kotlin.reflect.KClass


class TrackChangesWindow(val editor: CodeEditor) {
    val changesView = Composite(editor.display.shells.first(), SWT.BORDER)
    val table = Table(
        changesView,
        SWT.CHECK or SWT.BORDER or SWT.V_SCROLL or SWT.H_SCROLL
    )

    val plus = this::class.java.getClassLoader()
        .getResourceAsStream("icons${File.separator}plus.png")?.let {
            Image(Display.getDefault(), it)
        }

    val minus = this::class.java.getClassLoader()
        .getResourceAsStream("icons${File.separator}minus.png")?.let {
            Image(Display.getDefault(), it)
        }

    val edit = this::class.java.getClassLoader()
        .getResourceAsStream("icons${File.separator}edit.png")?.let {
            Image(Display.getDefault(), it)
        }

    init {
        //Label(editor.display.shells.first(), SWT.NONE).text = "???"
        //stickToMainWindow()
    }

    private fun stickToMainWindow() {
        val mainShell = editor.display.shells.first()
        mainShell.text = ClientProperties.clientName
        mainShell.size = Point(600, 600)
        mainShell.addControlListener(object : ControlAdapter() {
            override fun controlMoved(e: ControlEvent) {
                updateFollowerPosition()
            }

            override fun controlResized(e: ControlEvent) {
                updateFollowerPosition()
            }

            private fun updateFollowerPosition() {
                val y = mainShell.location.y + mainShell.size.y - 120
                changesView.setLocation(mainShell.location.x + 10, y)
                changesView.setSize(
                    mainShell.size.x - 20,
                    120
                )
            }
        })
    }


    fun open() {
        editor.display.shells.first().text = "${ClientProperties.clientName}: ${editor.folder}"
        changesView.layout = FillLayout()
        changesView.layoutData = GridData(SWT.FILL, SWT.FILL, true, false)
        table.headerVisible = true
        table.linesVisible = true
        table.addSelectionListener(object : SelectionAdapter() {
            override fun widgetSelected(e: SelectionEvent) {
                println("Selected item: ${e.item}")
                if (table.selection.isNotEmpty())
                    println(table.selection.get(0).data)
            }
        })

        val columnTitles = arrayOf("Transformation", "Incompatibility", "Reason")
        for (title in columnTitles) {
            val column = TableColumn(table, SWT.NONE)
            column.text = title
            column.width = 250
        }

//        table.addSelectionListener(object : SelectionAdapter() {
//            override fun widgetSelected(e: SelectionEvent) {
//                val item = e.item as TableItem
//                if(item.data is BodyDeclaration<*>)
//                    editor.classOnFocus?.focus(item.data as BodyDeclaration<*>)
//            }
//        })

        val popup = Menu(table)
        val push = MenuItem(popup, SWT.NONE)
        push.text = "Propagate"
        push.addListener(SWT.Selection) { e ->
            val selectedSet = table.items
                .filter { it.checked }
                .map { it.data as Transformation }
            try {
                Client.push(selectedSet)
            }
            catch (ex: Exception) {
               Display.getDefault().asyncExec {
                    MessageBox(Display.getDefault().activeShell, SWT.ICON_ERROR or SWT.OK).apply {
                        text = "Error"
                        message = ex.message
                    }.open()
                }
            }
        }

        val rollback = MenuItem(popup, SWT.NONE)
        rollback.text = "Rollback"
        rollback.addListener(SWT.Selection) { e ->
            TODO()
        }

//        val accept = MenuItem(popup, SWT.NONE)
//        accept.text = "Accept changes from A"
//        accept.addListener(SWT.Selection) { e ->
//
//        }

        table.menu = popup

        table.addListener(SWT.MenuDetect) { e: Event? ->
            val selection = table.selection
            if (selection.isEmpty()) {
                push.enabled = false
                rollback.enabled = false
//                accept.enabled = false
            } else {
                val selectedSet = table.items
                    .filter { it.checked }
                    .map { it.data as Transformation }

                push.enabled = Client.isConnected && selectedSet.isNotEmpty() && selectedSet.none { TrunkDelta.hasConflict(it) }
                rollback.enabled = true
//                accept.enabled = selectedSet.any { TrunkDelta.hasConflict(it) }
            }
        }
        changesView.requestLayout()
        //shell.open()
    }

    fun updateTable(list: List<Transformation>) {
        Display.getDefault().asyncExec {
            table.items.forEach { it.dispose() }
            for (t in list) {
                val item = TableItem(table, SWT.NONE)
                item.checked = true
                item.setImage(0, t.icon())
                item.setText(
                    arrayOf(
                        t.message(),
                        TrunkDelta.getConflictCollaborator(t),
                        TrunkDelta.getConflicts(t).asText()
                    )
                )
//                item.text = t.getText() + " (${t::class.simpleName})"
//                item.image = transIcon(t)
                if (TrunkDelta.hasConflict(t))
                    item.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_RED)


                item.data = t

            }
            table.requestLayout()
        }
    }

    fun updateConflicts() {
        Display.getDefault().asyncExec {
            table.items.forEach {
                val transformation = it.data as Transformation
                val conflicts = TrunkDelta.getConflicts(transformation)
                if (conflicts.isNotEmpty()) {
                    it.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_RED)
                    it.setText(1, conflicts.joinToString { it.collaborator })
                    it.setText(2, conflicts.asText())
                } else {
                    it.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_BLACK)
                    it.setText(1, "")
                    it.setText(2, "")
                }
            }
        }
    }

    private fun Transformation.icon(): Image? {
        return when (getText().split(" ")[0]) {
            "ADD" -> plus
            "REMOVE" -> minus
            "RENAME", "CHANGE" -> edit
            else -> null
        }
    }

    private fun Transformation.message(): String {
        return when (this) {
            is SignatureChanged -> if (nameChanged())
                "rename ${getNode().asString()} to '${getNewName()}'"
            else if (parametersChanged())
                "parameters changed: ${getNode().parameters} parameters to ${getNewParameters()}"
            else
                getText()

            is AddCallable, is BodyChangedCallable, is RemoveCallable -> {
                val callable =
                    this.getPrivateField("callable") as CallableDeclaration<*>
                callable.asString()
            }

            else -> getText()
        }
    }

    private fun CallableDeclaration<*>.asString(): String {
        return when (this) {
            is ConstructorDeclaration ->
                "contructor(${parameters.joinToString { it.typeAsString }})"

            is MethodDeclaration ->
                "${nameAsString}(${parameters.joinToString { it.typeAsString }})"

            else -> TODO()
        }
    }

    private fun Node.asString(): String {
        return this::class.asString()
    }

    private fun KClass<*>.asString(): String =
        when (this) {
            ClassOrInterfaceDeclaration::class -> if ((this as ClassOrInterfaceDeclaration).isInterface)
                "interface"
            else
                "class"

            FieldDeclaration::class -> "field"
            ConstructorDeclaration::class -> "constructor"
            MethodDeclaration::class -> "method"
            else -> this::class.simpleName ?: this.toString()
        }
}

private fun Iterable<ConflictInfo>.asText(): String {
    return joinToString("\n") { it.conflictMessage }
}



