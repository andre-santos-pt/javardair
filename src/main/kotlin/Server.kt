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
    private var conflicts: MutableMap<Pair<ClientHandler, ClientHandler>, Set<Conflict>> =
        mutableMapOf() // TODO Mudar estrutura.
    private val clientsInfoLock = Any()
    private val conflictsLock = Any() // TODO Rename
    var transformationsToApply = ""

    inner class ClientHandler(private val clientSocket: Socket) {
        private val writer: OutputStream = clientSocket.getOutputStream()
        private val reader: Scanner = Scanner(clientSocket.getInputStream())

        fun run() {
            try {
                serve()
            } catch (ex: Exception) {
                println("${clientSocket.port } closed the connection due to ${ex.printStackTrace()}")
            } finally {
                try {
                    clientSocket.close()
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
                            checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))

                            // check if all combination of conflicts possible were checked
                            if(conflicts.size == clientsInfo.size*(clientsInfo.size-1)/2) {
                                val allEmpty = conflicts.all { it.value.isEmpty() }
                                if (allEmpty) {
                                    // TODO Aplica as mudanças nos ficheiros do servidor aqui?
                                    val response = ServerMessage(ServerOperations.NOTIFY_CONFLICTS, "No conflicts!" )
                                    write(Json.encodeToString(response))
                                } else {
                                    conflicts.filter { it.value.isNotEmpty() }.forEach { (clientPair, conflicts) ->
                                        notifyConflictedClients(clientPair.first, clientPair.second, conflicts)
                                    }
                                }
                                conflicts.clear()
                            }
                        } else {
                            // TODO Nao acontece nada?
                        }
                    }

                    ClientOperations.PUSH -> {
                        // TODO Testar a ver se é preciso armazenar no hashmap as transformaçoes tbm, pq um client pode enviar mais do que o que o hashmap ja tem armazenado (no caso de fazer uma alteraçao que nao foi apanhada pela lista automatica)
                        applyChanges(Json.decodeFromString<JsonArray>(message.content))
                        if (clientsInfo.size > 1) {
                            propagateChanges(message.content)
                        }
                    }
                }
            }
        }

        private fun checkForConflicts(client: ClientHandler, trans: JsonArray) {
            clientsInfo.map { (otherClient, otherTrans) ->
                synchronized(conflictsLock) {
                    if(otherClient != client && !pairAlreadyExist(client, otherClient)) {
                        conflicts[Pair(client, otherClient)] = getConflicts(trans, otherTrans)
                    }
                }
            }
        }

        private fun pairAlreadyExist(client1: ClientHandler, client2: ClientHandler): Boolean {
            var result = false
            conflicts.map {
                if((it.key.first == client1 && it.key.second == client2) || (it.key.first == client2 && it.key.second == client1)) {
                    result = true
                }
            }
            return result
        }

        private fun getConflicts(transA: JsonArray, transB: JsonArray): Set<Conflict> {
            val transASerialized = transA.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(project)
            }.toMutableSet()
            val transBSerialized = transB.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(project)
            }.toMutableSet()
            val redundancyFreeSetOfTransformations = RedundancyFreeSetOfTransformations(transASerialized, transBSerialized)
            val setOfConflicts = getConflicts(project, redundancyFreeSetOfTransformations)
            return setOfConflicts
        }

        private fun notifyConflictedClients(first: ClientHandler, second: ClientHandler, conflict: Set<Conflict>) {
            try {
                val conflictMessage = conflict.map { "Conflict between ${it.first.getText()} and ${it.second.getText()} " }
                val response = ServerMessage(ServerOperations.NOTIFY_CONFLICTS, conflictMessage.toString() )
                first.write(Json.encodeToString(response))
                second.write(Json.encodeToString(response))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
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
                    val response = ServerMessage(ServerOperations.PROPAGATE, trans)
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
            clientsInfo[client] = JsonArray(emptyList()) // certo?
            thread { client.run() }
        }
    }
}

