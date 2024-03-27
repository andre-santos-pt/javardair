import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.DirectoryDialog
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.widgets.MessageBox
import org.eclipse.swt.widgets.Shell
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.Scanner
import kotlin.concurrent.thread


fun main() {
    val address = "localhost"
    val port = 8080
    val client = Client(address, port)
    client.run()
}

class Client(address: String, port: Int) {
    private val connection: Socket = Socket(address, port)
    private var connected: Boolean = true
    private lateinit var editor: CodeEditor
    private var ready: Boolean = false

    init {
        println("Connected to server at $address on port $port")
        thread { runJavardise() }
    }

    private val reader: Scanner = Scanner(connection.getInputStream())
    private val writer: OutputStream = connection.getOutputStream()

    fun run() {
        thread { read() }
        while (connected) {
            val input = readlnOrNull() ?: ""
            write(input)
        }
    }

    private fun write(message: String) {
        writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
    }

    private fun read() {
        while (connected)
            println(reader.nextLine())
    }

    private fun runJavardise() {
        // inicia instancia do javardise
        val display = Display()
        val fileDialog = DirectoryDialog(Shell(display))
        val root = File(fileDialog.open() ?: "")
        if (root.exists()) {
            editor = CodeEditor(display, root)
            editor.open()
        }
    }
}

