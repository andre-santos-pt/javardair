package pt.iscte.javardair

import pt.iscte.javardair.Client.getPrivatePath
import pt.iscte.javardair.messages.ConflictInfo
import pt.iscte.javardair.messages.FileContent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import pt.iscte.javardair.messages.ClientMessage
import pt.iscte.javardair.messages.ClientOperations
import pt.iscte.javardair.messages.ServerMessage
import pt.iscte.javardair.messages.ServerOperations
import model.*
import model.conflictDetection.Conflict
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path

fun main(args: Array<String>) {
    Server(8081, args.first()).launch()
}

class Server(val port: Int, val trunkPath: String) {
    private val project: Project
    private val clientsInfo: MutableMap<ClientHandler, JsonArray> = mutableMapOf()
    private val clientsInfoLock = Any()

    init {
        project = Project(trunkPath)
        File(trunkPath).walk(FileWalkDirection.TOP_DOWN).forEach {
           println(it)
        }
        launch()
    }

    fun launch() {
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

            // Transformar Map<ClientHandler, List<Conflict> em Map<String, List<pt.iscte.javardair.messages.ConflictInfo>
            conflicts.forEach { (clientHandler, conflicts) ->
                val tempMap = mutableMapOf<String, Set<ConflictInfo>>()
                val conflictInfoSet = conflicts.map { conflict ->
                    ConflictInfo(
                        "Conflict between ${
                            (conflict.first.toJson()["code"]).toString()
                                .trim('"')
                        } and ${
                            conflict.second.toJson()["code"].toString()
                                .trim('"')
                        }",
                        conflict.first.getNode().uuid.toString(),
                        conflict.second.toJson(),
                        conflict.second.getText()
                    )
                }.toSet()
                val conflictInfoSetOpposite = conflicts.map { conflict ->
                    ConflictInfo(
                        "Conflict between ${
                            conflict.first.toJson()["code"].toString().trim('"')
                        } and ${
                            conflict.second.toJson()["code"].toString()
                                .trim('"')
                        }",
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
                    fileListTemp.add(
                        FileContent(
                            it.name,
                            content
                        )
                    )
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
}

