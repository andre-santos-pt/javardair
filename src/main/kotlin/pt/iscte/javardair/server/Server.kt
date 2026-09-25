package pt.iscte.javardair.server

import kotlinx.serialization.json.*
import pt.iscte.javardair.client.ClientMessage
import pt.iscte.javardair.client.ClientOperation
import model.*
import model.conflictDetection.Conflict
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import model.transformations.BodyChangedCallable
import model.transformations.SignatureChanged
import pt.iscte.javardair.JsonPretty
import pt.iscte.javardair.decodeTransformations
import pt.iscte.javardair.toJson
import pt.iscte.javardair.toTransformation
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.Path

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: java -jar javardair.jar <port> [<trunkPath>]")
        return
    }
    val port = args[0].toIntOrNull()
    if (port == null || port !in 1..65535) {
        System.err.println("Invalid port number: ${args[0]}. Port must be an integer between 1 and 65535.")
        return
    }
    val trunkPath =
        if (args.size == 2) args[1] else System.getProperty("user.dir")
    if (!File(trunkPath).exists()) {
        System.err.println("Working directory does not exist: $trunkPath")
        return
    }
    if (!File(trunkPath).isDirectory) {
        System.err.println("Working directory is not a directory: $trunkPath")
        return
    }
    Server(port, File(trunkPath).absolutePath).launch()
}

class Server(val port: Int, val trunkPath: String) {
    private val project: Project = Project(trunkPath)
    private val clientTransformations = mutableMapOf<ClientHandler, JsonArray> ()
    private val clientsLock = Any()

    init {
        launch()
    }

    fun launch() {
        val serverSocket = ServerSocket(port)
        println("Javardair Server started on port $port; trunk path: $trunkPath")
        while (true) {
            val clientSocket = serverSocket.accept()
            thread { ClientHandler(clientSocket).run() }
        }
    }

    inner class ClientHandler(private val clientSocket: Socket) {
        private val writer: OutputStream = clientSocket.getOutputStream()
        private val reader: Scanner = Scanner(clientSocket.getInputStream())
        private lateinit var clientID: String
        private lateinit var clientName: String

        fun run() {
            println("New connection accepted on port ${clientSocket.port}")
            synchronized(clientsLock) {
                clientTransformations[this] = JsonArray(emptyList())
            }
            try {
                serve()
            } catch (ex: Exception) {
                println("${clientSocket.port} closed the connection due to ${ex.message}")
                ex.printStackTrace()
            } finally {
                try {
                    clientSocket.close()
                    synchronized(clientsLock) {
                        clientTransformations.remove(this)
                    }
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }
        }

        private fun write(message: String) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }



        private fun serve() {
            while (true) {
                val text = reader.nextLine()
                val message = Json.decodeFromString<ClientMessage>(text)
                println("${message.op} [${if(::clientName.isInitialized) clientName else "?"}]: ${JsonPretty.print(message.content)}\n")
                when (message.op) {
                    ClientOperation.HANDSHAKE -> {
                        val s = Json.decodeFromString<String>(message.content)
                        val (receivedClientID, receivedClientName) = s.split(
                            ","
                        )
                        clientID = receivedClientID
                        clientName = receivedClientName
                        println("Connected $clientName: $clientID")
                        // TODO no answer?
                    }

                    ClientOperation.FETCH_REQUEST -> {
                        sendFiles()
                    }

                    // Checks if there are any conflicts with the other clients.
                    ClientOperation.UPDATE -> {
                        val transformations =
                            Json.decodeFromString<JsonArray>(message.content)
                        synchronized(clientsLock) {
                            clientTransformations[this] = transformations
                        }
                        if (clientTransformations.size > 1) {
                            val conflicts =
                                checkForConflicts(this, transformations)
                            notifyConflicts(this, conflicts)
                        }
                    }

                    ClientOperation.PROPAGATE -> {
                        val transformations =
                            Json.decodeFromString<JsonArray>(message.content)
                        synchronized(clientsLock) {
                            clientTransformations[this] = transformations
                        }
                        if (clientTransformations.size > 1) {
                            // if there is more than one client connected, check for conflicts with the other clients
                            val conflicts =
                                checkForConflicts(this, transformations)
                            val allEmpty = conflicts.all { it.value.isEmpty() }
                            if (allEmpty) {
                                // If there are no conflicts, apply changes and propagate it.
                                applyChanges(transformations)
                                propagateChanges(message.content)
                            } else {
                                // Notify clients for the specific conflicts.
                                notifyConflicts(this, conflicts)
                            }
                        } else {
                            applyChanges(transformations)
                            propagateChanges(message.content)
                        }
                    }

                    // TODO
                    ClientOperation.FORCE_PUSH -> {
                        val transformations =
                            Json.decodeFromString<JsonArray>(message.content)
                        synchronized(clientsLock) {
                            clientTransformations[this] = transformations
                        }
                        if (clientTransformations.size > 1) {
                            applyChanges(transformations)
                            propagateChanges(message.content)
                        } else {
                            // Nao pode haver conflitos pq é o unico que esta connectado.
                            applyChanges(transformations)
                            propagateChanges(message.content)
                        }
                    }
                }
            }
        }


        private fun sendFiles() {
            val dir = File(project.getProjectRoot().root.toString())
            val files = dir.listFiles()
            val fileListTemp = mutableListOf<FileContent>()

            files?.forEach {
                if (it.isFile) {
                    val content = Base64.getEncoder()
                        .encodeToString(Files.readAllBytes(it.toPath()))
                    fileListTemp.add(
                        FileContent(
                            it.name,
                            content
                        )
                    )
                }
            }

            val fileList = Json.encodeToString(fileListTemp)

            val message = ServerMessage(
                ServerOperation.FETCH_RESPONSE,
                fileList,
                this.clientID
            )
            write(Json.encodeToString(message))
        }

        private fun notifyConflicts(
            client: ClientHandler,
            conflicts: MutableMap<ClientHandler, Set<Conflict>>
        ) {

            // Criar um novo MutableMap para lidar com o facto de ClientHandler e Conflict nao serem Serilaizble
            val newMap: MutableMap<String, Set<ConflictInfo>> =
                mutableMapOf()

            // Transformar Map<ClientHandler, List<Conflict> em Map<String, List<pt.iscte.javardair.server.ConflictInfo>
            conflicts.forEach { (clientHandler, conflicts) ->
                val tempMap = mutableMapOf<String, Set<ConflictInfo>>()
                val conflictInfoSet = conflicts.map { conflict ->
                    ConflictInfo(
                        clientHandler.clientName,
                        conflict.userMessage(),
                        conflict.first.getNode().uuid.toString(),
                        conflict.second.toJson(project),
                        conflict.first.toJson(project)
                    )
                }.toSet()
                val conflictInfoSetOpposite = conflicts.map { conflict ->
                    ConflictInfo(
                        client.clientName,
                        conflict.userMessage(),
                        conflict.second.getNode().uuid.toString(),
                        conflict.first.toJson(project),
                        conflict.second.toJson(project)
                    )
                }.toSet()
                tempMap["${this.clientID},${this.clientName}"] =
                    conflictInfoSetOpposite
                newMap["${clientHandler.clientID},${clientHandler.clientName}"] =
                    conflictInfoSet
                val response = ServerMessage(
                    ServerOperation.NOTIFY_CONFLICTS,
                    Json.encodeToString(tempMap),
                    this.clientID
                )
                clientHandler.write(Json.encodeToString(response))
            }
            val response = ServerMessage(
                ServerOperation.NOTIFY_CONFLICTS,
                Json.encodeToString(newMap),
                this.clientID
            )
            write(Json.encodeToString(response))
        }

        private fun Conflict.userMessage(): String {
            fun SignatureChanged.toText() = getNewParameters().joinToString(prefix = "(", postfix = ")") { it.nameAsString + " " + it.typeAsString  }
            return if(first is SignatureChanged && second is SignatureChanged) {
                if((first as SignatureChanged).getNewName() != (second as SignatureChanged).getNewName())
                    "Renames: ${(first as SignatureChanged).getNewName()} vs. ${(second as SignatureChanged).getNewName()}"
                else
                    "Parameters: ${(first as SignatureChanged).toText()} vs. ${(second as SignatureChanged).toText()}"
            }
            else if(first is BodyChangedCallable && second is BodyChangedCallable)
                "Different method body"
            else
                this.message
        }

        private fun propagateChanges(trans: String) {
            try {
                clientTransformations.keys.forEach {
                    val response = ServerMessage(
                        ServerOperation.PROPAGATE,
                        trans,
                        this.clientID
                    )
                    it.write(Json.encodeToString(response))
                }
            } catch (ex: Exception) {
                println("Could not send message to other clients $ex")
            }
        }
    }

    // Returns a map with the conflict of the client with the other clients.
    private fun checkForConflicts(
        client: ClientHandler,
        trans: JsonArray
    ): MutableMap<ClientHandler, Set<Conflict>> {
        val conflicts: MutableMap<ClientHandler, Set<Conflict>> =
            mutableMapOf()
        synchronized(clientsLock) {
            clientTransformations.map { (otherClient, otherTrans) ->
                if (otherClient != client) {
                    conflicts[otherClient] =
                        getConflicts(trans, otherTrans)
                }
            }
            return conflicts
        }
    }

    // Get a set of conflicts between two List of Transformations.
    private fun getConflicts(
        transA: JsonArray,
        transB: JsonArray
    ): Set<Conflict> {
        val transASerialized = transA.decodeTransformations(project, project.path).toMutableSet()
        val transBSerialized = transB.decodeTransformations(project, project.path).toMutableSet()

        val redundancyFreeSetOfTransformations =
            RedundancyFreeSetOfTransformations(
                transASerialized,
                transBSerialized
            )
        return getConflicts(project, redundancyFreeSetOfTransformations)
    }

    private fun applyChanges(transformations: JsonArray) {
        try {
            val trans = transformations.decodeTransformations(project, project.path).toSet()
            applyTransformationsTo(project, trans)
            project.saveProjectTo(Path(project.path))

        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}


