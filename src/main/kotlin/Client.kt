import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

    private fun sendChanges() {
        try {
            val currentTransformations = FactoryOfTransformations(projectRoot, projectLocal).getListOfAllTransformations().toMutableSet()
            val serializedTransformations = JsonArray(currentTransformations.map { it.toJson() })
            val message = Message(Operations.PULL, Json.encodeToString(serializedTransformations))
            println("Sending transformation list: $message")
            write(Json.encodeToString(message))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun dealWithServer() {
        try {
            while (isConnected) {
                val message = reader.nextLine()
                val resp = Json.decodeFromString<Message>(message)
                println("Received message: $resp")
                when(resp.op) {
                    Operations.PUSH -> {
                        applyChanges(Json.decodeFromString(resp.content))
                    }
                    Operations.PULL -> {
                        sendChanges()
                    }
                    Operations.NOTIFY_CONFLICTS -> {
                        notifyConflicts(resp.content)
                    }
                    Operations.FETCH -> TODO()
                    Operations.REQUEST_ROOT_FILE -> {
                        //updateRootFile(resp.content)
                    }
                }
            }
        } catch (ex: Exception) {
            println("Disconnected from server: $ex")
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
           val message = Message(Operations.REQUEST_ROOT_FILE, "")
           write(Json.encodeToString(message))
       }
    }

    private fun notifyConflicts(conflicts: String) {
        println(conflicts)
    }
}

