import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import model.Project
import org.eclipse.swt.widgets.Display
import pt.iscte.javardise.demo.toTransformation
import pt.iscte.javardise.editor.CodeEditor
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.Scanner
import kotlin.concurrent.thread

object Client {
    private const val address: String = "localhost" // mudar
    private const val port: Int = 8080 // mudar
    private lateinit var socket: Socket
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    private lateinit var projectBranch: Project
    private lateinit var projectBase: Project
    var isConnected = false

    fun open(projectBranch: Project, projectBase: Project) {
        isConnected = true
        this.projectBranch = projectBranch
        this.projectBase = projectBase
        runClient()
    }
    private fun runClient() {
        try {
            connectToServer()
        } catch (ex: Exception) {
            println("Cannot connect to the server ${ex.printStackTrace()}")
        }
    }

    private fun connectToServer() {
        socket = Socket(address, port)
        reader = Scanner(socket.getInputStream())
        writer = socket.getOutputStream()
        thread { dealWithServer() }
    }

    fun close() {
        socket.close()
        isConnected = false
    }

    fun writeMessage(message: String) {
        if(isConnected) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }
    }

    private fun applyChanges(serializedTransformations: String) {
        try {
            val test = (Json.parseToJsonElement(serializedTransformations) as JsonObject).toTransformation(projectBranch)
            Display.getDefault().syncExec { test.applyTransformation(projectBranch) }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

    }


    // é okay lidar assim? ou devo criar uma inner classe que seja uma thread dps?
    private fun dealWithServer() {
        // TODO lidar com as mensagens que vai receber do servidor para fazer mudanças ao ficheiro
        try {
            while (isConnected) {
                val text = reader.nextLine()
                println(text)
                if (text != "Welcome to the server") applyChanges(text)
            }

        } catch (ex: Exception) {
            println("Disconnected from server: $ex")
        }
    }
}

