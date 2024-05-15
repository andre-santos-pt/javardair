import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import messages.ClientMessage
import messages.ClientOperations
import messages.ServerMessage
import messages.ServerOperations
import model.FactoryOfTransformations
import model.Project
import model.applyTransformationsTo
import org.eclipse.swt.widgets.Display
import pt.iscte.javardise.demo.toJson
import pt.iscte.javardise.demo.toTransformation
import java.io.File
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
    internal lateinit var projectLocal: Project
    internal lateinit var projectRoot: Project
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
        thread {
            //requestRootFile()
            dealWithServer()
        }
    }

    fun close() {
        socket.close()
        isConnected = false
    }

    private fun dealWithServer() {
        try {
            while (isConnected) {
                val text = reader.nextLine()
                // The client will only receive messages from the server
                val message = Json.decodeFromString<ServerMessage>(text)
                println("Received message: $message")
                when(message.op) {
                    ServerOperations.FETCH_RESPONSE -> TODO()

                    ServerOperations.PROPAGATE -> {
                        applyChanges(Json.decodeFromString(message.content))
                    }

                    ServerOperations.NOTIFY_CONFLICTS -> {
                        notifyConflicts(message.content)
                    }
                }
            }
        } catch (ex: Exception) {
            println("Disconnected from server: ${ex.printStackTrace()}")
        }
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

    private fun applyChanges(serializedTransformations: JsonArray) {
        try {
            val transRoot = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectRoot)
            }
            val transLocal = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectLocal)
            }
            Display.getDefault().syncExec {
                applyTransformationsTo(projectLocal, transLocal.toSet())
                //projectLocal.saveProjectTo(Path(projectLocal.getPrivatePath()))
            }
            applyTransformationsTo(projectRoot, transRoot.toSet())
            projectRoot.saveProjectTo(Path(projectRoot.getPrivatePath()))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

    }

    private fun updateRootFile(file: String) {
        try {
            File(projectRoot.getProjectRoot().root.toString() + "\\Test.java").writeText(file) //TODO considera a trans de AddFile
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun requestRootFile() {
       if(isConnected) {
           val message = ClientMessage(ClientOperations.FETCH_REQUEST, "")
           write(Json.encodeToString(message))
       }
    }

    private fun notifyConflicts(conflicts: String) {
        println(conflicts)
    }
}

