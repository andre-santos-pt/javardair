package pt.iscte.javardair.client

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import model.FactoryOfTransformations
import model.Project
import model.applyTransformationsTo
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import model.getConflicts
import model.transformations.Transformation
import org.eclipse.swt.widgets.Display
import pt.iscte.javardair.decodeTransformations
import pt.iscte.javardair.server.ConflictInfo
import pt.iscte.javardair.server.FileContent
import pt.iscte.javardair.server.ServerMessage
import pt.iscte.javardair.server.ServerOperation
import pt.iscte.javardair.toJson
import pt.iscte.javardise.external.getOrNull
import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.net.Socket
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.io.path.exists


class Client(
    var projectTrunk: Project,
    val projectLocal: Project,
    val trunkDelta: TrunkDelta
) {
    private var socket: Socket? = null
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    var isConnected = false
        private set

    private val clientID: UUID = UUID.randomUUID()

    var errorHandler: (ClientError) -> Unit = {}

    var trunkUpdateEvent: (Project) -> Unit = {}

    var propagationEvent: () -> Unit = {}

    var newFileEvent: (CompilationUnit) -> Unit = {}

    var connectionEvent: (Boolean) -> Unit = {}

    internal fun addLocalJavaFile(unit: CompilationUnit) {
        projectLocal.addFile(unit)
    }

    private val deltaObserver = { transformations: List<Transformation> ->
        if (isConnected) {
            val trans =
                JsonArray(transformations.map { it.toJson(projectLocal) })
            ClientOperation.UPDATE.send(trans)
        }
    }

    data class ClientError(
        val title: String,
        val message: String,
        val exception: Exception? = null
    )

    fun connect() {
        try {
            socket = Socket(ClientProperties.address, ClientProperties.port)
            socket?.let { socket ->
                reader = Scanner(socket.getInputStream())
                writer = socket.getOutputStream()
                thread {
                    ClientOperation.HANDSHAKE.send(JsonPrimitive("$clientID,${ClientProperties.clientName}"))
                    ClientOperation.FETCH_REQUEST.send(JsonPrimitive(null))
                    dealWithServer()
                }
            }
            isConnected = true
            connectionEvent(true)
        } catch (ex: Exception) {
            error(
                "Connection Error",
                "Cannot connect to the server at ${ClientProperties.address}:${ClientProperties.port}",
                ex
            )
        }
    }

    fun disconnect() {
        socket?.close()
        isConnected = false
        connectionEvent(false)
        trunkDelta.removeObserver(deltaObserver)
    }

    private fun error(
        title: String,
        message: String,
        exception: Exception? = null
    ) {
        errorHandler.invoke(ClientError(title, message, exception))
    }

    fun ClientOperation.send(json: JsonElement) {
        if (isConnected) {
            try {
                val message = Json.encodeToString(
                    ClientMessage(this, Json.encodeToString(json))
                )
                writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
            } catch (ex: Exception) {
                error(this.name, ex.message ?: "Unknown error", ex)
            }
        } else {
            error(
                "Not connected",
                "Cannot send message to server because the client is not connected."
            )
        }
    }

    private fun dealWithServer() {
        trunkDelta.addObserver(deltaObserver)

        try {
            while (isConnected) {
                val text = reader.nextLine()
                // The client will only receive messages from the server
                val message = Json.decodeFromString<ServerMessage>(text)
                println("[${message.op}] ${message.content}")
                when (message.op) {
                    ServerOperation.FETCH_RESPONSE -> {
                        updateRootFiles(Json.decodeFromString(message.content))
                    }

                    ServerOperation.PROPAGATE -> {
                        // forcar o focus a sair
                        // ver se ha conflitos com a current lista de trans
                        // se houver, guardar esta current list numa var extra
                        // aplicar as mudanças vindas do propagate
                        // aplicar as mundanças da current list
//                        integratePropagation(
//                            Json.decodeFromString(message.content),
//                            message.sender
//                        )
                        applyChanges(
                            Json.decodeFromString(message.content),
                            message.sender
                        )
                        propagationEvent()
                        trunkDelta.updateTransformations()
                    }

                    ServerOperation.NOTIFY_CONFLICTS -> {
                        trunkDelta.updateConflicts(Json.decodeFromString(message.content))
                    }
                }
            }
        } catch (ex: Exception) {
            error(
                "Disconnected",
                "Disconnected from server at ${ClientProperties.address}:${ClientProperties.port}",
                ex
            )
            ex.printStackTrace()
            disconnect()
        }
    }

    private fun updateRootFiles(fileList: List<FileContent>) {
        val rootDir = File(projectTrunk.getProjectRoot().root.toString())

        // clear the root directory before updating files
        rootDir.listFiles()?.forEach { it.deleteRecursively() }

        fileList.forEach {
            val filePath = "$rootDir${File.separator}${it.fileName}"
            val file = File(filePath)
            val decodedContent = Base64.getDecoder().decode(it.fileContent)
            file.writeBytes(decodedContent)
        }
        // reinitialize the projectTrunk to reflect the updated files
        projectTrunk = Project(projectTrunk.getProjectRoot().root.toString())
        trunkUpdateEvent(projectTrunk)

        projectTrunk.getSetOfCompilationUnit().toList().forEach {
            val filePath = "${projectLocal.path}${File.separator}${it.storage.getOrNull?.fileName}"
            if(!File(filePath).exists()) {
                val w = PrintWriter(filePath)
                w.write(it.toString())
                w.close()
                val unit = StaticJavaParser.parse(File(filePath))
                addLocalJavaFile(unit)
                newFileEvent(unit)
            }
        }
        // trigger comparison of transformations between projectLocal and projectTrunk
        trunkDelta.updateTransformations()
    }


    // safe mechanism to deal with the case of user making a change while receiving a PROPAGATE message
    private fun integratePropagation(forcedTrans: JsonArray, sender: String) {

        // sends current transformation list to the server
        fun updateServer() {
            if (isConnected) {
                val trans = JsonArray(
                    FactoryOfTransformations(projectTrunk, projectLocal)
                        .getListOfAllTransformations()
                        .map { it.toJson(projectLocal) }
                )
                ClientOperation.UPDATE.send(trans)
            }
        }

        fun setsAreEqual(
            a: Set<Transformation>,
            b: Set<Transformation>
        ): Boolean {
            if (a.size != b.size) return false
            val list1 = a.map { it.toJson(projectLocal).toString() }.sorted()
            val list2 = b.map { it.toJson(projectLocal).toString() }.sorted()
            return list1 == list2
        }

        val currentTrans = FactoryOfTransformations(projectTrunk, projectLocal)
            .getListOfAllTransformations()
            .toMutableSet()

        // check if conflicts exist between current changes and trans being forced into
        val forcedTransSerialized =
            forcedTrans.decodeTransformations(projectTrunk, projectLocal.path).toMutableSet()

        applyChanges(forcedTrans, sender)
        trunkDelta.updateTransformations()

        if (setsAreEqual(currentTrans, forcedTransSerialized)) {
            updateServer()
        } else {
            val redundancyFreeSetOfTransformations =
                RedundancyFreeSetOfTransformations(
                    forcedTransSerialized,
                    currentTrans
                )
            val conflicts =
                getConflicts(projectLocal, redundancyFreeSetOfTransformations)

            // apply changes normally (to both local and root project)

            // apply the current changes to the local only
            if (conflicts.isNotEmpty()) {
                Display.getDefault().syncExec {
                    applyTransformationsTo(projectLocal, currentTrans.toSet())
                }
            }
            updateServer()
        }
    }


    private fun applyChanges(
        serializedTransformations: JsonArray,
        sender: String?
    ) {
        try {
            if (sender != clientID.toString()) {
                projectLocal.initializeAllIndexes()
                val transLocal =
                    serializedTransformations.decodeTransformations(projectTrunk, projectLocal.path)
                Display.getDefault().syncExec {
                    applyTransformationsTo(projectLocal, transLocal.toSet())

                }
                projectLocal.getSetOfCompilationUnit()
                    .filter { !Path(it.storage.getOrNull?.path.toString()).exists() }
                    .forEach {
                        val w =
                            PrintWriter(it.storage.getOrNull?.path.toString())
                        w.write(it.toString())
                        w.close()
                        newFileEvent(it)
                    }
            }

            val transRoot =
                serializedTransformations.decodeTransformations(projectTrunk, projectTrunk.path)
            applyTransformationsTo(projectTrunk, transRoot.toSet())
            projectTrunk.saveProjectTo(Path(projectTrunk.path))

            trunkDelta.updateTransformations()
        } catch (ex: Exception) {
            error(
                "Apply Changes Error",
                "Could not apply changes: ${ex.message}",
                ex
            )
        }
    }





    fun propagate(transformations: List<Transformation>) {
        if (transformations.any { trunkDelta.hasConflict(it) })
            error(
                "Conflicts",
                "Cannot push changes because there are conflicts in the selected transformation set."
            )
        else {
            val trans =
                JsonArray(transformations.map { it.toJson(projectLocal) })
            ClientOperation.PROPAGATE.send(trans)
        }
    }

    fun acceptChanges(conflict: ConflictInfo) {
        try {
            projectLocal.initializeAllIndexes()
            val transLocal =
                JsonArray(listOf(conflict.conflictingTransformation)).decodeTransformations(
                    projectTrunk, projectLocal.path
                ).toSet()
            Display.getDefault().syncExec {
                applyTransformationsTo(projectLocal, transLocal)
            }

        } catch (ex: Exception) {
            error(
                "Apply Changes Error",
                "Could not apply changes: ${ex.message}",
                ex
            )
        }
        trunkUpdateEvent(projectTrunk)
        propagationEvent()
        trunkDelta.updateTransformations()
    }

}

