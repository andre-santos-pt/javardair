import Client.getPrivatePath
import com.google.gson.Gson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import messages.ClientMessage
import messages.ClientOperations
import messages.ServerMessage
import messages.ServerOperations
import model.*
import model.conflictDetection.Conflict
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import pt.iscte.javardise.demo.toJson
import pt.iscte.javardise.demo.toTransformation
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path

fun main() {
    Server(8080)
}
class Server(port: Int) {
    private lateinit var project: Project
    private val clientsInfo: MutableMap<ClientHandler, JsonArray> = mutableMapOf()
    private val clientsInfoLock = Any()

    inner class ClientHandler(private val clientSocket: Socket) {
        private val writer: OutputStream = clientSocket.getOutputStream()
        private val reader: Scanner = Scanner(clientSocket.getInputStream())
        private lateinit var clientID: String
        private lateinit var clientName: String

        fun run() {
            try {
                serve()
            } catch (ex: Exception) {
                println("${clientSocket.port } closed the connection due to ${ex.printStackTrace()}")
            } finally {
                try {
                    clientSocket.close()
                    clientsInfo.remove(this)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }
        }

        private fun serve() {
            while (true) {
                val text = reader.nextLine()
                val message = Json.decodeFromString<ClientMessage>(text) // The server will only receive messages from the client.
                when (message.op) {
                    ClientOperations.HANDSHAKE -> {
                        val (receivedClientID, receivedClientName) = message.content.split(",")
                        clientID = receivedClientID
                        clientName = receivedClientName
                        println("$clientID : $clientName")
                    }

                    ClientOperations.FETCH_REQUEST -> {
                        sendFiles()
                    }

                    // Checks if there are any conflicts with the other clients.
                    ClientOperations.UPDATE -> {
                        synchronized(clientsInfoLock) {
                            clientsInfo[this] = Json.decodeFromString<JsonArray>(message.content)
                        }
                        if (clientsInfo.size > 1) {
                            val conflicts = checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))
                            notifyConflicts(conflicts)
                        }
                    }

                    ClientOperations.PUSH -> {
                        synchronized(clientsInfoLock) {
                            clientsInfo[this] = Json.decodeFromString<JsonArray>(message.content)
                        }
                        if (clientsInfo.size > 1) {
                            val conflicts = checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))
                            // If there are 0 conflicts, apply changes and propagate it.
                            val allEmpty = conflicts.all { it.value.isEmpty() }
                            if(allEmpty) {
                                applyChanges(Json.decodeFromString<JsonArray>(message.content))
                                propagateChanges(message.content)
                            } else {
                                // Notify clients for the specific conflicts.
                                notifyConflicts(conflicts)
                            }
                        }
                        else {
                            applyChanges(Json.decodeFromString<JsonArray>(message.content))
                            propagateChanges(message.content)
                        }
                    }

                    ClientOperations.FORCE_PUSH -> {
                        synchronized(clientsInfoLock) {
                            clientsInfo[this] = Json.decodeFromString<JsonArray>(message.content)
                        }
                        if (clientsInfo.size > 1) {
                            applyChanges(Json.decodeFromString<JsonArray>(message.content))
                            propagateChanges(message.content)

                        }
                        else {
                            // Nao pode haver conflitos pq é o unico que esta connectado.
                            applyChanges(Json.decodeFromString<JsonArray>(message.content))
                            propagateChanges(message.content)
                        }
                    }
                }
            }
        }

        private fun notifyConflicts(conflicts: MutableMap<ClientHandler, Set<Conflict>>) {
            // Criar um novo MutableMap para lidar com o facto de ClientHandler e Conflict nao serem Serilaizble
            val newMap: MutableMap<String, Set<ConflictInfo>> = mutableMapOf()

            // Transformar Map<ClientHandler, List<Conflict> em Map<String, List<ConflictInfo>
            conflicts.forEach { (clientHandler, conflicts) ->
                val tempMap = mutableMapOf<String, Set<ConflictInfo>>()
                val conflictInfoSet = conflicts.map { conflict ->
                    ConflictInfo(
                        "Conflict between ${(conflict.first.toJson()["code"]).toString().trim('"')} and ${conflict.second.toJson()["code"].toString().trim('"')}",
                        conflict.first.getNode().uuid.toString(),
                        conflict.second.toJson(),
                        conflict.second.getText()
                    )
                }.toSet()
                val conflictInfoSetOpposite = conflicts.map { conflict ->
                    ConflictInfo(
                        "Conflict between ${conflict.first.toJson()["code"].toString().trim('"')} and ${conflict.second.toJson()["code"].toString().trim('"')}",
                        conflict.second.getNode().uuid.toString(),
                        conflict.first.toJson(),
                        conflict.first.getText()
                    )
                }.toSet()
                tempMap["${this.clientID},${this.clientName}"] = conflictInfoSetOpposite
                newMap["${clientHandler.clientID},${clientHandler.clientName}"] = conflictInfoSet
                val response = ServerMessage(
                    ServerOperations.NOTIFY_CONFLICTS,
                    Json.encodeToString(tempMap),
                    this.clientID
                )
                clientHandler.write(Json.encodeToString(response))
            }
            val response = ServerMessage(
                ServerOperations.NOTIFY_CONFLICTS,
                Json.encodeToString(newMap),
                this.clientID
            )
            write(Json.encodeToString(response))
        }

        // Returns a map with the conflict of the client with the other clients.
        private fun checkForConflicts(client: ClientHandler, trans: JsonArray): MutableMap<ClientHandler, Set<Conflict>> {
            val conflicts: MutableMap<ClientHandler, Set<Conflict>> = mutableMapOf()
            synchronized(clientsInfoLock) {
                clientsInfo.map { (otherClient, otherTrans) ->
                    if(otherClient != client) {
                        conflicts[otherClient] = getConflicts(trans, otherTrans)
                    }
                }
                return conflicts
            }
        }

        // Get a Set of conflicts between two List of Transformations.
        private fun getConflicts(transA: JsonArray, transB: JsonArray): Set<Conflict> {
            val transASerialized = transA.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(project)
            }.toMutableSet()
            val transBSerialized = transB.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(project)
            }.toMutableSet()
            val redundancyFreeSetOfTransformations =
                RedundancyFreeSetOfTransformations(transASerialized, transBSerialized)
            return getConflicts(project, redundancyFreeSetOfTransformations)
        }

        private fun write(message: String) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }

        private fun applyChanges(transformations: JsonArray) {
            try {
                val trans = transformations.map { json ->
                    (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(project)
                }
                applyTransformationsTo(project, trans.toSet())
                project.saveProjectTo(Path(project.getPrivatePath()))   

            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        private fun propagateChanges(trans: String) {
            try {
                clientsInfo.keys.forEach {
                    val response = ServerMessage(
                        ServerOperations.PROPAGATE,
                        trans,
                        this.clientID
                    )
                    it.write(Json.encodeToString(response))

                }
            } catch (ex: Exception) {
                println("Could not send message to other clients $ex")
            }
        }

        private fun sendFiles() {
            val dir = File(project.getProjectRoot().root.toString())
            val files = dir.listFiles()
            val fileListTemp = mutableListOf<FileContent>()

            files?.forEach {
                if (it.isFile) {
                    val content = Base64.getEncoder().encodeToString(Files.readAllBytes(it.toPath()))
                    fileListTemp.add(FileContent(
                        it.name,
                        content
                    ))
                }
            }

            val fileList = Json.encodeToString(fileListTemp)

            println(fileList)

            val message = ServerMessage(
                ServerOperations.FETCH_RESPONSE,
                fileList,
                this.clientID
            )

            write(Json.encodeToString(message))
        }

    }

    private fun loadFiles() {
        writeFile("server/Test.java", """
                //9e30e98a-36db-47f4-836c-16c390a1d2d7
                package test;

                //13c9f311-0d07-46aa-8591-ef22c6ab8e49
                class Test {

                    //d0779f95-d537-4501-b708-fc50747e6616
                    void method(int param) {

                    }
                }
        """.trimIndent())
    }

    private fun writeFile(path: String, src:String) {
        val file = File(path)
        if(!Files.exists(file.toPath())) {
            Files.createDirectories(file.parentFile.toPath());
            PrintWriter(file).use { out ->
                out.println(src)
            }
        }
        project = Project(file.parentFile.path)
    }

    init {
        loadFiles()
        val serverSocket = ServerSocket(port)
        println("Server started on port $port")
        while (true) {
            val clientSocket = serverSocket.accept()
            println("Client connected: ${clientSocket.port}")
            val client = ClientHandler(clientSocket)
            clientsInfo[client] = JsonArray(emptyList())
            thread { client.run() }
        }
    }
}

