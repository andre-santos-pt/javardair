package pt.iscte.javardair

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import com.github.javaparser.symbolsolver.utils.SymbolSolverCollectionStrategy
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import model.FactoryOfTransformations
import model.Project
import model.applyTransformationsTo
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import model.getConflicts
import model.transformations.Transformation
import org.eclipse.swt.widgets.Display
import pt.iscte.javardair.messages.*
import java.io.File
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path


object Client {
    private var socket: Socket? = null
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    internal lateinit var projectLocal: Project
    internal lateinit var projectTrunk: Project
    var isConnected = false
    private val clientID: UUID = UUID.randomUUID() // TODO sera que este uuid devia ser criado quando a ide é aberta e nao quando o cliente se junta?

    fun open(editorPath: File, allCompilationUnits: List<CompilationUnit>) {
        val memoryTypeSolver = MemoryTypeSolver()

        val trunkDir = File(editorPath, ClientProperties.trunkFolder)
        if(!trunkDir.exists())
            trunkDir.mkdirs()

        projectTrunk = Project(trunkDir.absolutePath)
        projectLocal = Project(
            editorPath.absolutePath,
            SymbolSolverCollectionStrategy().collect(
                Path(editorPath.absolutePath)
            ),
            null,
            allCompilationUnits.toMutableList(),
            CombinedTypeSolver(ReflectionTypeSolver(false), memoryTypeSolver),
            memoryTypeSolver,
            true,
            true
        )
        isConnected = true
        try {
            connectToServer()
        } catch (ex: Exception) {
            println("${ClientProperties.clientName}: Cannot connect to the server on port ${ClientProperties.port}")
        }
    }

    private fun connectToServer() {
        socket = Socket(ClientProperties.address, ClientProperties.port)
        socket?.let { socket ->
            reader = Scanner(socket.getInputStream())
            writer = socket.getOutputStream()
            thread {
                sendHandshakeMessage()
                requestFiles()
                dealWithServer()
            }
        }
    }

    fun close() {
        socket?.close()
        isConnected = false
    }

    private fun dealWithServer() {
        TrunkDelta.addObserver {
            if(isConnected) {
                val serializedTransformations = JsonArray(it.map { it.toJson() })
                try {
                    val message = ClientMessage(ClientOperations.UPDATE, Json.encodeToString(serializedTransformations))
                    write(Json.encodeToString(message))
                    println("update: $message")
                } catch (ex: Exception) {
                    println("Could not send message to Server ${ex.printStackTrace()}")
                }
            }
        }

        try {
            while (isConnected) {
                val text = reader.nextLine()
                // The client will only receive messages from the server
                val message = Json.decodeFromString<ServerMessage>(text)
                when(message.op) {
                    ServerOperations.FETCH_RESPONSE -> {
                        updateRootFiles(Json.decodeFromString(message.content))
                    }

                    ServerOperations.PROPAGATE -> {
                        // forcar o focus a sair
                        // ver se ha conflitos com a current lista de trans
                        // se houver, guardar esta current list numa var extra
                        // aplicar as mudanças vindas do propagate
                        // aplicar as mundanças da current list
                        checkChanges(Json.decodeFromString(message.content), message.sender)
                    }

                    ServerOperations.NOTIFY_CONFLICTS -> {
                        TrunkDelta.updateConflicts(Json.decodeFromString(message.content))
//                        notifyConflicts(Json.decodeFromString(message.content))
                    }
                }
            }
        } catch (ex: Exception) {
            println("Disconnected from server at ${ClientProperties.address}:${ClientProperties.port}")
        }
    }

    // safe mechanism to deal with the case of user making a change while receiving a PROPAGATE message
    private fun checkChanges(forcedTrans: JsonArray, sender: String) {
        // get current changes
        val currentTrans = mutableSetOf<Transformation>()
        val factoryOfTransformations = FactoryOfTransformations(projectTrunk, projectLocal)
        currentTrans.addAll(factoryOfTransformations.getListOfAllTransformations())

        // check if conflicts exist between current changes and trans being forced into
        val forcedTransSerialized = forcedTrans.map { json ->
            (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(
                projectTrunk
            ) // da erro se for Local pq em teoria o UUID e nao esta la. É aqui que esta a haver o erro de mudar um metodo adicionado
        }.toMutableSet()
        forcedTransSerialized.forEach { println("forcedTrans: ${it.toJson()}") }

        applyChanges(forcedTrans, sender)
        TrunkDelta.updateTransformations()

        if(setsAreEqual(currentTrans, forcedTransSerialized)){
            updateServer()

        } else {
            val redundancyFreeSetOfTransformations = RedundancyFreeSetOfTransformations(forcedTransSerialized, currentTrans)
            val conflicts = getConflicts(projectLocal, redundancyFreeSetOfTransformations)

            // apply changes normally (to both local and root project)

            // apply the current changes to the local only
            if(conflicts.isNotEmpty()) {
                // TODO Verificar se ele depois vai ver as difs bem
                Display.getDefault().syncExec {
                    applyTransformationsTo(projectLocal, currentTrans.toSet())
                }
            }
            updateServer()
        }
    }

    private fun setsAreEqual(set1: MutableSet<Transformation>, set2: MutableSet<Transformation>): Boolean {
        if (set1.size != set2.size) return false

        val list1 = set1.map { it.toJson().toString() }.sorted()
        val list2 = set2.map { it.toJson().toString() }.sorted()

        return list1 == list2
    }

    fun write(message: String) {
        if(isConnected) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }
    }

    private fun applyChanges(serializedTransformations: JsonArray, sender: String?) {
        try {
            projectLocal.initializeAllIndexes()

            val transRoot = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(
                    projectTrunk
                )
            }

            if(sender != clientID.toString()) {
                val transLocal = serializedTransformations.map { json ->
                    (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(
                        projectLocal
                    ) // aqui dava erro tambem quando se edita um metodo que foi adicionado (no client q o adicionou)
                }

                Display.getDefault().syncExec {
                    applyTransformationsTo(projectLocal, transLocal.toSet())
                }
            }

            applyTransformationsTo(projectTrunk, transRoot.toSet())
            projectTrunk.saveProjectTo(Path(projectTrunk.getPrivatePath()))
        } catch (ex: Exception) {
            println("Could not apply changes. ${ex.printStackTrace()}")        }
    }

    private fun applyChangesLocal(serializedTransformations: JsonArray) {
        try {
            projectLocal.initializeAllIndexes()
            val transRoot = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(
                    projectLocal
                )
            }
            println("LOCAL : " + transRoot)
            applyTransformationsTo(projectLocal, transRoot.toSet())
            projectLocal.saveProjectTo(Path(projectLocal.getPrivatePath()))
        } catch (ex: Exception) {
            println("Could not apply changes. ${ex.printStackTrace()}")        }
    }

    // send current transformation list to the server for consistency matters
    private fun updateServer() {
        val transformations: MutableSet<Transformation> = mutableSetOf()
        val factoryOfTransformations = FactoryOfTransformations(projectTrunk, projectLocal)
        transformations.addAll(factoryOfTransformations.getListOfAllTransformations())

        if(isConnected) {
            val tempTrans = JsonArray(transformations.map { it.toJson() })
            try {
                val message = ClientMessage(ClientOperations.UPDATE, Json.encodeToString(tempTrans))
                write(Json.encodeToString(message))

            } catch (ex: Exception) {
                println("Could not send message to Server. ${ex.printStackTrace()}")
            }
        }
    }
    private fun updateRootFiles(fileList: List<FileContent>) {
        // Get dir from current client
        val rootDir = File(projectTrunk.getProjectRoot().root.toString())

        // Update/Create files
        fileList.forEach {
            val filePath = "$rootDir${File.separator}${it.fileName}"
            val file = File(filePath)
            val decodedContent = Base64.getDecoder().decode(it.fileContent)
            file.writeBytes(decodedContent)
        }
        TrunkDelta.updateTransformations()
        //projectTrunk = Project(trunkDir.absolutePath)
    }

    private fun requestFiles() {
       if(isConnected) {
           val message = ClientMessage(ClientOperations.FETCH_REQUEST, "")
           write(Json.encodeToString(message))
       }
    }

    private fun sendHandshakeMessage() {
        if(isConnected) {
            val message = ClientMessage(
                ClientOperations.HANDSHAKE,
                "$clientID,${ClientProperties.clientName}"
            )
            write(Json.encodeToString(message))
        }
    }

    fun push(transformations: List<Transformation>) {
        if (!isConnected)
            throw RuntimeException("Not connected")
        else if (transformations.any { TrunkDelta.hasConflict(it) })
            throw RuntimeException("There are conflicts in the transformation set")
        else {
            val serializedTransformations =  JsonArray(transformations.map { it.toJson() })
            try {
                val message = ClientMessage(
                    ClientOperations.PUSH,
                    Json.encodeToString(serializedTransformations)
                )
                write(Json.encodeToString(message))
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw RuntimeException("Could not send PUSH to Server")
            }
        }
    }

    fun acceptChanges(conflict: ConflictInfo) {
        applyChangesLocal(JsonArray(listOf(conflict.conflictingTransformation)))
        TrunkDelta.updateTransformations()
    }
}

