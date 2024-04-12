import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import model.Project
import model.rootPath
import org.eclipse.swt.widgets.Display
import pt.iscte.javardise.demo.toTransformation
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.Scanner
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.reflect.jvm.isAccessible

object Client {
    private const val address: String = "localhost" // mudar
    private const val port: Int = 8080 // mudar
    private lateinit var socket: Socket
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    internal lateinit var projectBranch: Project
    internal lateinit var projectBase: Project
    var isConnected = false

    fun open() {
        isConnected = true
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

    fun write(message: String) {
        if(isConnected) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }
    }

    // temp hack
    fun Project.getPrivatePath(): String {
        val f = this::class.members.find { it.name == "path" }
        f!!.isAccessible = true
        return f.call(this).toString()
    }

    private fun applyChanges(serializedTransformations: String) {
        try {
            val trans = (Json.parseToJsonElement(serializedTransformations) as JsonObject).toTransformation(projectBranch)
            trans.applyTransformation(projectBase)
            projectBase.saveProjectTo(Path(projectBase.getPrivatePath()))
            Display.getDefault().syncExec { trans.applyTransformation(projectBranch) }
            projectBase.getSetOfCompilationUnit().forEach { println(it) }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

    }

    private fun dealWithServer() {
        try {
            while (isConnected) {
                val message = reader.nextLine()
                val resp = Json.decodeFromString<Response>(message)
                println("Received changes: $resp")
                if(resp.op == Operations.PUSH) {
                    applyChanges(resp.trans)
                }
            }

        } catch (ex: Exception) {
            println("Disconnected from server: $ex")
        }
    }
}

