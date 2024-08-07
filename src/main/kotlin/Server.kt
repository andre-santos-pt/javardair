import Client.getPrivatePath
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
import java.util.Scanner
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
                println("Received message from $clientSocket: $message")
                when (message.op) {
                    ClientOperations.FETCH_REQUEST -> TODO()

                    // Checks if there are any conflicts with the other clients.
                    ClientOperations.UPDATE -> {
                        synchronized(clientsInfoLock) {
                            clientsInfo[this] = Json.decodeFromString<JsonArray>(message.content) // este lock aqui é necessario? mesmo que dois clients metam coisas ao mesmo tempo vai ser semppre em posicoes dif
                        }
                        if (clientsInfo.size > 1) {
                            val conflicts = checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))
                            println("Conflitos: $conflicts")
                            notifyConflicts(conflicts)
                        }
                    }

                    // TODO mudar aqui, o push tem de fazer a verificaçao de conflitos tbm e enviar para os clientes?
                    ClientOperations.PUSH -> {
                        synchronized(clientsInfoLock) {
                            clientsInfo[this] = Json.decodeFromString<JsonArray>(message.content) // este lock aqui é necessario? mesmo que dois clients metam coisas ao mesmo tempo vai ser semppre em posicoes dif
                        }
                        if (clientsInfo.size > 1) {
                            val conflicts = checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))

                            // If there are 0 conflicts, apply changes and propagate it.
                            val allEmpty = conflicts.all { it.value.isEmpty() }
                            if(allEmpty) {
                                applyChanges(Json.decodeFromString<JsonArray>(message.content))
                                propagateChanges(message.content)
                                // TODO Devo avisar aqui tbm que nao há conflitos?
                            } else {
                                // Notify clients for the specific conflicts.
                                notifyConflicts(conflicts)
                            }
                        }
                        else {
                            // Nao pode haver conflitos pq é o unico que esta connectado.
                            applyChanges(Json.decodeFromString<JsonArray>(message.content))
                        }
                    }

                    ClientOperations.HANDSHAKE -> {
                        clientID = message.content
                        println(clientID)
                    }
                }
            }
        }

        // TODO onde é que devo meter as threads? Uma thread para fazer esta tarefa toda, ou iniciar threads so para enviar as mensagens?
        private fun notifyConflicts(conflicts: MutableMap<ClientHandler, Set<Conflict>>) {
            // Criar um novo MutableMap para lidar com o facto de ClientHandler e Conflict nao serem Serilaizble
            val newMap: MutableMap<String, Set<ConflictInfo>> = mutableMapOf()

            // Transformar Map<ClientHandler, List<Conflict> em Map<String, List<ConflictInfo>
            conflicts.forEach { (clientHandler, conflicts) ->
                val tempMap = mutableMapOf<String, Set<ConflictInfo>>()
                val conflictInfoSet = conflicts.map { conflict ->
                    ConflictInfo(
                        "Conflict between ${conflict.first.toJson()} and ${conflict.second.toJson()}",
                        conflict.first.getNode().uuid.toString(),
                        conflict.second.toJson()
                    )
                }.toSet()
                // TODO Havera uma forma mais eficiente de fazer isto? Sobre diferenciar que Trans mudar
                val conflictInfoSetOpposite = conflicts.map { conflict ->
                    ConflictInfo(
                        "Conflict between ${conflict.first.toJson()} and ${conflict.second.toJson()}",
                        conflict.first.getNode().uuid.toString(),
                        conflict.first.toJson() //TODO Resolver este problema, tem que se meter toJson()
                    )
                }.toSet()
                tempMap[this.clientID] = conflictInfoSetOpposite
                newMap[clientHandler.clientID] = conflictInfoSet
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
                // TODO talvez fazer uma thread para cada uma iteraçao do for
                clientsInfo.map { (otherClient, otherTrans) ->
                    if(otherClient != client) {
                        conflicts[otherClient] = getConflicts(trans, otherTrans)
                        println("Conflito com $otherClient -> ${conflicts[otherClient]}")
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
                project.saveProjectTo(Path(project.getPrivatePath())) // TODO as vezes da um erro : I am not a child of my parent.

            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        private fun propagateChanges(trans: String) {
            try {
                println("Propagating changes to all users.")
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
        // everytime the server is initiated it loads a new set of files - TESTING PURPOSES
        val file = File(path)
        //Files.deleteIfExists(file.toPath())
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

