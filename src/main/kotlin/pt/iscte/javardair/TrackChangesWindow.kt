package pt.iscte.javardair

import model.transformations.Transformation
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.Shell
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.TableItem
import pt.iscte.javardise.editor.CodeEditor
import java.io.File


class TrackChangesWindow(val editor: CodeEditor) : Observer {
    val shell = Shell(Display.getDefault())
    val table = Table(
        shell,
        SWT.CHECK or SWT.BORDER or SWT.V_SCROLL or SWT.H_SCROLL
    )

    val plus = this::class.java.getClassLoader().getResourceAsStream("icons${File.separator}plus.png")?.let {
        Image(Display.getDefault(), it)
    }

    val minus = this::class.java.getClassLoader().getResourceAsStream("icons${File.separator}minus.png")?.let {
        Image(Display.getDefault(), it)
    }

    val edit = this::class.java.getClassLoader().getResourceAsStream("icons${File.separator}edit.png")?.let {
        Image(Display.getDefault(), it)
    }

    fun open() {
        shell.text = "Javardair"
        shell.setSize(400, 300)
        shell.layout = FillLayout()

        table.headerVisible = false
        table.linesVisible = true
//        val columnTitles = arrayOf("Push", "Transformation", "Info")
//        for (title in columnTitles) {
//            val column = TableColumn(table, SWT.NONE)
//            column.setText(title)
//            column.width = 200
//        }

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

    override fun update(list: MutableSet<Transformation>) {
        Display.getDefault().asyncExec {
            table.items.forEach { it.dispose() }
            for (t in list) {
                val item = TableItem(table, SWT.NONE)
                item.setImage(0, transIcon(t))
                item.setText(arrayOf(t.getText(), "${t::class.simpleName}"))
//                item.text = t.getText() + " (${t::class.simpleName})"
//                item.image = transIcon(t)
                item.data = t.getNode()

            }
            table.requestLayout()
        }
    }

    private fun transIcon(transformation: Transformation): Image? {
        return when(transformation.getText().split(" ")[0]) {
            "ADD" -> plus
            "REMOVE" -> minus
            "RENAME", "CHANGE" -> edit
            else -> null

        }
    }
}