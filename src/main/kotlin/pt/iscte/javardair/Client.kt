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
import model.*
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import model.transformations.Transformation
import org.eclipse.swt.widgets.Display
import pt.iscte.javardair.messages.*
import java.io.File
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.*
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.reflect.jvm.isAccessible

const val trunkFolder: String = ".trunk"

object Client {
    private const val address: String = "localhost" // TODO mudar arg
    private const val port: Int = 8080 // TODO mudar arg
    private lateinit var socket: Socket
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    internal lateinit var projectLocal: Project
    internal lateinit var projectTrunk: Project
    var isConnected = false
    private lateinit var conflictsMap: ObservableConflictMap
    //private lateinit var conflictView: ConflictView
    private val clientID: UUID = UUID.randomUUID() // TODO sera que este uuid devia ser criado quando a ide é aberta e nao quando o cliente se junta?
    private lateinit var clientName: String

    fun open(editorPath: File, allCompilationUnits: List<CompilationUnit>) {
        val memoryTypeSolver = MemoryTypeSolver()

        val trunkDir = File(editorPath, trunkFolder)
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
        conflictsMap = ObservableConflictMap(mutableMapOf())
        //conflictView = ConflictView()
        clientName = projectLocal.getProjectRoot().root.fileName.toString().substringAfter("workspace_") // TODO mudar
        runClient()
    }

    private fun runClient() {
        try {
            connectToServer()
            //conflictsMap.addObserver(conflictView)
        } catch (ex: Exception) {
            println("Cannot connect to the server ${ex.printStackTrace()}")
        }
    }

    private fun connectToServer() {
        socket = Socket(address, port)
        reader = Scanner(socket.getInputStream())
        writer = socket.getOutputStream()
        thread {
            sendHandshakeMessage()
            requestFiles()
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
                        notifyConflicts(Json.decodeFromString(message.content))
                    }
                }
            }
        } catch (ex: Exception) {
            println("Disconnected from server: ${ex.printStackTrace()}")
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
        CentralizedList.updateTransformations()

        if(setsAreEqual(currentTrans, forcedTransSerialized)){
            updateServer()

        } else {
            val redundancyFreeSetOfTransformations = RedundancyFreeSetOfTransformations(forcedTransSerialized, currentTrans)
            var conflicts = getConflicts(projectLocal, redundancyFreeSetOfTransformations)

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

    // temp hack
    fun Project.getPrivatePath(): String {
        val f = this::class.members.find { it.name == "path" }
        f!!.isAccessible = true
        return f.call(this).toString()
    }

    private fun applyChanges(serializedTransformations: JsonArray, sender: String) {
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
        CentralizedList.updateTransformations()
        //projectTrunk = Project(trunkDir.absolutePath)
    }

    private fun requestFiles() {
       if(isConnected) {
           val message = ClientMessage(ClientOperations.FETCH_REQUEST, "")
           write(Json.encodeToString(message))
       }
    }

    private fun notifyConflicts(conflicts: MutableMap<String, Set<ConflictInfo>>) {
        conflicts.forEach { (client, conflictSet) ->
            conflictsMap[client] = conflictSet.toMutableList()
            conflictsMap.notifyObservers()
        }
    }

    fun isConflictFree(): Boolean {
        return conflictsMap.all { it.value.isEmpty() }
    }

    private fun sendHandshakeMessage() {
        if(isConnected) {
            val message = ClientMessage(
                ClientOperations.HANDSHAKE,
                "$clientID,$clientName"
            )
            write(Json.encodeToString(message))
        }
    }
}

