import Client.getPrivatePath
import kotlinx.serialization.Serializable
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
                            // TODO Idealmente era enviar este map diretamente para o cliente
                            val conflicts = checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))
                            println("Conflitos: $conflicts")

                            // todo percorrer o map e criar uma lista [(otherClient, list<conflictInfo())] basicamente transformar o conflict em conflict info so pq o conflict nao é serializable (se puder mudar melhor)
                            val newMap: MutableMap<String, Set<ConflictInfo>> = mutableMapOf()

                            conflicts.forEach { (clientHandler, conflicts) ->
                                val tempMap = mutableMapOf<String, Set<ConflictInfo>>()

                                val conflictInfoSet = conflicts.map { conflict ->
                                    ConflictInfo(
                                        "Conflict between ${conflict.first.toJson()} and ${conflict.second.toJson()}",
                                        conflict.first.getNode().uuid.toString(),
                                    )
                                }.toSet()

                                tempMap[this.clientSocket.port.toString()] = conflictInfoSet
                                newMap[clientHandler.clientSocket.port.toString()] = conflictInfoSet

                                val response = ServerMessage(
                                    ServerOperations.NOTIFY_CONFLICTS,
                                    Json.encodeToString(tempMap)
                                )

                                clientHandler.write(Json.encodeToString(response))
                            }

                            println("Novo hashmap: $newMap")

                            val response = ServerMessage(
                                ServerOperations.NOTIFY_CONFLICTS,
                                Json.encodeToString(newMap)
                            )

                            println("Mensagem que iria enviar: $response")

                            write(Json.encodeToString(response))



                            //TODO se nao ha conflitos com nenhum cliente, entao dizer ao client q fez a mudança que nao ha nada, e limpar o hashmap?
                            /**if (allEmpty) {
                                val tempList = listOf<ConflictInfo>()
                                val response = ServerMessage(ServerOperations.NOTIFY_CONFLICTS, Json.encodeToString(tempList))
                                //TODO A ideia de avisar todos os clientes que nao ha conflito quando UM deles faz a mudança nao é boa, pq permite q outros clientes q tenham outros conflitos possam fazer um submit (que vai ser rejeitado)
                                //TODO devia se avisar que nao ha conflitos naquele node
                                thread {
                                    clientsInfo.keys.forEach {
                                        it.write(Json.encodeToString(response))
                                    }
                                }
                            } else {
                            **/

                            /**
                            val conflictList = mutableListOf<ConflictInfo>()

                            conflicts.forEach { (otherClient, conflictSet) ->
                                println("A iterar cada conflito: $otherClient -> $conflictSet")
                                val otherClientConflictList = mutableListOf<ConflictInfo>()
                                if (conflictSet.isNotEmpty()) {
                                    conflictSet.forEach {
                                        conflictList.add(
                                            ConflictInfo(
                                                "Conflict between ${it.first.toJson()} and ${it.second.toJson()}",
                                                it.first.getNode().uuid.toString(),
                                                otherClient.clientSocket.port.toString()
                                            )
                                        )
                                        otherClientConflictList.add(
                                            ConflictInfo(
                                                "Conflict between ${it.first.toJson()} and ${it.second.toJson()}",
                                                it.first.getNode().uuid.toString(),
                                                this.clientSocket.port.toString()
                                            )
                                        )
                                    }
                                    println("Lista de conflitos para os outros clientes")
                                    val response = ServerMessage(
                                        ServerOperations.NOTIFY_CONFLICTS,
                                        Json.encodeToString(otherClientConflictList.toList())
                                    )
                                    otherClient.write(Json.encodeToString(response))
                                } else {
                                    //TODO avisar o cliente q fez mandou o update que nao ha conflitos com especifico cliente
                                    //TODO avisar o outroCliente que nao ha conflito com o client que fez o update
                                    sendNoConflictMessage(this, otherClient.clientSocket.port.toString())
                                    sendNoConflictMessage(otherClient, this.clientSocket.port.toString())
                                }

                            }
                            println("lista de todos os conflitos em formato novo: $conflictList")
                            val response = ServerMessage(
                                ServerOperations.NOTIFY_CONFLICTS,
                                Json.encodeToString(conflictList.toList())
                            )
                            write(Json.encodeToString(response))
                            **/
                        }

                    }

                    ClientOperations.PUSH -> {
                        synchronized(clientsInfoLock) {
                            clientsInfo[this] = Json.decodeFromString<JsonArray>(message.content) // este lock aqui é necessario? mesmo que dois clients metam coisas ao mesmo tempo vai ser semppre em posicoes dif
                        }
                        if (clientsInfo.size > 1) {
                            val conflicts = checkForConflicts(this, Json.decodeFromString<JsonArray>(message.content))
                            val allEmpty = conflicts.all { it.value.isEmpty() }
                            if(allEmpty) {
                                // TODO Devo avisar aqui tbm que nao há conflitos? Nao acho que seja necessario -> SIM
                                applyChanges(Json.decodeFromString<JsonArray>(message.content))
                                propagateChanges(message.content)
                            } else {
                                conflicts.filter { it.value.isNotEmpty() }.forEach {
                                    //notifyConflictedClients(this, it.key, it.value)
                                }
                            }
                        }
                        else {
                            // Nao pode haver conflitos pq é o unico que esta connectado.
                            applyChanges(Json.decodeFromString<JsonArray>(message.content))
                        }
                    }
                }
            }
        }

        fun sendNoConflictMessage(client: ClientHandler, conflictedPair: String) {
            //TODO idealmente aqui mandava uma lista vazia, mas no lado do cliente é preciso saber qual o conflictedPair
            val noConflictList = listOf(ConflictInfo("No conflicts", ""))
            val response = ServerMessage(ServerOperations.NOTIFY_CONFLICTS, Json.encodeToString(noConflictList))
            client.write(Json.encodeToString(response))
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

        private fun notifyConflictedClients(first: ClientHandler, second: ClientHandler, conflicts: Set<Conflict>) {
            try {
                // TODO mudar o conteudo do conflictMessage
                val conflictMessage = conflicts.map { "Conflict between ${it.first.toJson()} and ${it.second.toJson()} " }
                val conflictList = conflicts.map {
                    ConflictInfo(
                        "Conflict between ${it.first.toJson()} and ${it.second.toJson()}",
                        it.first.getNode().uuid.toString(),
                    )
                }
                //println(conflictMessage)
                println("Conflict list: $conflictList")
                val response = ServerMessage(ServerOperations.NOTIFY_CONFLICTS, Json.encodeToString(conflictList))
                first.write(Json.encodeToString(response))
                second.write(Json.encodeToString(response)) // esta mensagem nao pode ser igual
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
            clientsInfo[client] = JsonArray(emptyList())
            thread { client.run() }
        }
    }
}

