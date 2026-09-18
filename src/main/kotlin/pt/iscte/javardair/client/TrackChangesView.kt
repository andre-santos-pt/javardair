package pt.iscte.javardair.client

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import model.transformations.*
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.widgets.*
import org.eclipse.swt.widgets.Event
import pt.iscte.javardair.JsonPretty
import pt.iscte.javardair.getPrivateField
import pt.iscte.javardair.server.ConflictInfo
import pt.iscte.javardair.toJson
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import kotlin.reflect.KClass


class TrackChangesWindow(val editor: CodeEditor) {

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

    val changesView = Composite(editor.getPrivateField("shell") as Shell, SWT.BORDER)
    val table = Table(
        changesView,
        SWT.CHECK or SWT.BORDER or SWT.V_SCROLL or SWT.H_SCROLL
    )

    val debugView = Composite(editor.getPrivateField("shell") as Shell, SWT.BORDER).apply {
        layout = FillLayout()
        Text(this, SWT.MULTI or SWT.V_SCROLL or SWT.H_SCROLL).apply {
            text = "debug"
            layoutData = GridData(SWT.FILL, SWT.FILL, true, true)
        }
    }.children.first() as Text

   init {
        editor.display.shells.first().text = "${ClientProperties.clientName}: ${editor.folder}"
        changesView.layout = FillLayout()
        changesView.layoutData = GridData(SWT.FILL, SWT.FILL, true, false)
        table.headerVisible = true
        table.linesVisible = true
        table.addSelectionListener(object : SelectionAdapter() {
            override fun widgetSelected(e: SelectionEvent) {
                if (table.selection.isNotEmpty()) {
                    val json = (table.selection[0].data as Transformation).toJson()
                        .toString()
                    debugView.text = JsonPretty.print(json)
                    debugView.requestLayout()
                }
                else {
                    debugView.text = "debug"
                    debugView.requestLayout()
                }
            }
        })

        val columnTitles = arrayOf("Transformation", "Incompatibility", "Reason")
        for (title in columnTitles) {
            val column = TableColumn(table, SWT.NONE)
            column.text = title
            column.width = 250
        }

        createPopupMenu()

        TrunkDelta.addObserver {
            updateTable(it)
        }
        TrunkDelta.addConflictObserver {
            updateConflicts()
        }
    }

    private fun createPopupMenu() {
        val popup = Menu(table)
        val push = MenuItem(popup, SWT.NONE)
        push.text = "Propagate"
        push.addListener(SWT.Selection) { e ->
            val selectedSet = table.items
                .filter { it.checked }
                .map { it.data as Transformation }
            try {
                Client.propagate(selectedSet)
                TrunkDelta.updateTransformations()
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

        val rollback = MenuItem(popup, SWT.NONE)
        rollback.text = "Rollback"
        rollback.addListener(SWT.Selection) { e ->

        }

        table.menu = popup

        table.addListener(SWT.MenuDetect) { e: Event? ->
            val selection = table.selection
            if (selection.isEmpty()) {
                push.enabled = false
                rollback.enabled = false
            } else {
                val selectedSet = table.items
                    .filter { it.checked }
                    .map { it.data as Transformation }

                push.enabled =
                    Client.isConnected && selectedSet.isNotEmpty() && selectedSet.none {
                        TrunkDelta.hasConflict(it)
                    }
                rollback.enabled = true // TODO: check if rollback is possible
            }
        }
    }

    private fun updateTable(list: List<Transformation>) {
        Display.getDefault().asyncExec {
            val selected = table.items.filter { it.checked}.map { it.getText(0) }
            table.items.forEach { it.dispose() }
            for (t in list) {
                val item = TableItem(table, SWT.NONE)
                item.checked = selected.contains(t.message())
                item.setImage(0, t.icon())
                item.setText(
                    arrayOf(
                        t.message(),
                        TrunkDelta.getConflictCollaborator(t),
                        TrunkDelta.getConflicts(t).asText()
                    )
                )
                if (TrunkDelta.hasConflict(t))
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
                "${getNode().signature} parameters changed to (${getNewParameters().joinToString { it.typeAsString }})"
            else
                getText()

            is AddCallable -> {
                val callable = this.getPrivateField("callable") as CallableDeclaration<*>
                this.getParentNode().nameAsString + "." + callable.asString()
            }

            is BodyChangedCallable, is RemoveCallable -> {
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



