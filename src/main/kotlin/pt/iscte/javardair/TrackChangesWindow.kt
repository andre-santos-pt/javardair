package pt.iscte.javardair

import model.transformations.SignatureChanged
import model.transformations.Transformation
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ControlAdapter
import org.eclipse.swt.events.ControlEvent
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.*
import pt.iscte.javardise.editor.CodeEditor
import java.io.File


class TrackChangesWindow(val editor: CodeEditor) {
    val shell = Shell(Display.getDefault(), SWT.NO_TRIM or SWT.ON_TOP)
    val table = Table(
        shell,
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
        val mainShell = editor.display.shells.first()
        mainShell.addControlListener(object : ControlAdapter() {
            override fun controlMoved(e: ControlEvent) {
                println("move")
                updateFollowerPosition()
            }

            override fun controlResized(e: ControlEvent) {
                updateFollowerPosition()
            }

            private fun updateFollowerPosition() {
                val y = mainShell.location.y + mainShell.size.y - 200
                shell.setLocation(mainShell.location.x + 10, y)
                shell.setSize(
                    mainShell.size.x - 20,
                    200
                )
            }
        })
    }

    fun open() {
        shell.text = "Javardair: ${editor.folder}"
        shell.setSize(600, 400)
        shell.layout = FillLayout()

        table.headerVisible = true
        table.linesVisible = true
        val columnTitles = arrayOf("Transformation", "type", "Conflict")
        for (title in columnTitles) {
            val column = TableColumn(table, SWT.NONE)
            column.setText(title)
            column.width = 200
        }

//        table.addSelectionListener(object : SelectionAdapter() {
//            override fun widgetSelected(e: SelectionEvent) {
//                val item = e.item as TableItem
//                if(item.data is BodyDeclaration<*>)
//                    editor.classOnFocus?.focus(item.data as BodyDeclaration<*>)
//            }
//        })
        shell.requestLayout()
        shell.open()
    }

    fun updateTable(list: List<Transformation>) {
        Display.getDefault().asyncExec {
            table.items.forEach { it.dispose() }
            for (t in list) {
                val item = TableItem(table, SWT.NONE)
                item.setImage(0, t.icon())
                item.setText(
                    arrayOf(
                        t.message(),
                        "${t::class.simpleName}",
                        TrunkDelta.getConflictMessage(t)
                    )
                )
//                item.text = t.getText() + " (${t::class.simpleName})"
//                item.image = transIcon(t)
                if (TrunkDelta.hasConflict(t))
                    item.foreground =
                        Display.getDefault().getSystemColor(SWT.COLOR_RED)

                item.data = t.getNode()

            }
            table.requestLayout()
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
            is SignatureChanged -> if(nameChanged())
                "rename: ${getNode().name} to ${getNewName()}"
            else if(parametersChanged())
                "parameters changed: ${getNode().parameters} parameters to ${getNewParameters()}"
            else
                getText()
            else -> getText()
        }
    }
}