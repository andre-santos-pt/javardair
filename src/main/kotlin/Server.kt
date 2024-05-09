import Client.getPrivatePath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
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
    lateinit var clientList: ArrayList<ClientHandler>
    private lateinit var project: Project
    private val clientsInfo: MutableMap<ClientHandler, JsonArray> = mutableMapOf()
    private var conflicts: MutableMap<Pair<ClientHandler, ClientHandler>, Set<Conflict>> = mutableMapOf()
    private val lock = Any()
    var transformationsToApply = ""

    inner class ClientHandler(private val clientSocket: Socket) {
        private val writer: OutputStream = clientSocket.getOutputStream()
        private val reader: Scanner = Scanner(clientSocket.getInputStream())

        fun run() {
            try {
                serve()
            } catch (ex: Exception) {
                println("${clientSocket.inetAddress.hostAddress} closed the connection")
            } finally {
                try {
                    clientSocket.close()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }

            }
        }

        private fun serve() {
            while(true) {
                val message = reader.nextLine()
                val resp = Json.decodeFromString<Message>(message)
                println("Received message from $clientSocket: $resp")
                when(resp.op) {
                    Operations.PUSH -> {
                        transformationsToApply = resp.content
                        clientsInfo[this] = Json.decodeFromString<JsonArray>(resp.content)
                        if(clientsInfo.size > 1) {
                            requestChanges(clientsInfo.keys) // TODO Mudar para o hashmap
                        } else {
                            // TODO tecnicamente nunca pode ser 0 (o tamanho) - mas se for vai dar erro
                            applyChanges(Json.decodeFromString<JsonArray>(transformationsToApply))
                            propagateChanges(transformationsToApply, clientsInfo.keys)
                        }

                    }
                    Operations.PULL -> {
                        clientsInfo[this] = Json.decodeFromString<JsonArray>(resp.content)
                        clientsInfo.map { (client, trans) ->
                            synchronized(lock)  {
                                if(client != this && !pairAlreadyExist(this, client)) {
                                    conflicts[Pair(this, client)] = checkConflicts(Json.decodeFromString<JsonArray>(resp.content), trans )
                                }
                            }
                        }

                        // only sends conflict message if it has asked every client for their changes

                        if(conflicts.size == clientsInfo.size*(clientsInfo.size-1)/2) {
                            val allEmpty = conflicts.all { it.value.isEmpty() }
                            if (allEmpty) {
                                applyChanges(Json.decodeFromString<JsonArray>(transformationsToApply))
                                propagateChanges(transformationsToApply, clientsInfo.keys)
                            } else {
                                conflicts.filter { it.value.isNotEmpty() }.forEach { (clientPair, conflicts) ->
                                    notifyConflictedClients(clientPair.first, clientPair.second, conflicts)
                                }
                            }
                            conflicts.clear()
                        }
                    }
                    Operations.REQUEST_ROOT_FILE -> {
                        //sendRootFile()
                    }
                    Operations.NOTIFY_CONFLICTS -> TODO()
                    Operations.FETCH -> TODO()
                }
            }
        }

        // TODO - Fazer de uma forma menos "hardcoded"
        private fun pairAlreadyExist(client1: Server.ClientHandler, client2: Server.ClientHandler): Boolean {
            var result = false
            conflicts.map {
                if((it.key.first == client1 && it.key.second == client2) || (it.key.first == client2 && it.key.second == client1)) {
                    result = true
                }
            }
            return result
        }

        private fun notifyConflictedClients(first: ClientHandler, second: ClientHandler, conflict: Set<Conflict>) {
            try {
                val conflictMessage = conflict.map { "Conflict between ${it.first.getText()} and ${it.second.getText()} " }
                val request = Message(Operations.NOTIFY_CONFLICTS, conflictMessage.toString() )
                first.write(Json.encodeToString(request))
                second.write(Json.encodeToString(request))
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        private fun sendRootFile() {
            val files = JsonArray(project.getSetOfCompilationUnit().map { FileContent(Path(it.path).fileName.toString(), it.toString()).toJson() })

            val message = Message(Operations.REQUEST_ROOT_FILE, Json.encodeToString(files))
            //println(message)

           //val test = Json.decodeFromString<JsonArray>(Json.decodeFromString<Message>(Json.encodeToString(message)).content).map {
             //  (Json.parseToJsonElement(it.toString()) as JsonObject).toFileContent()
           //}
        }

        private fun checkConflicts(transA: JsonArray, transB: JsonArray): Set<Conflict> {
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

        private fun write(message: String) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }

        private fun propagateChanges(transformations: String, clientList: MutableSet<ClientHandler>) {
            try {
                println("Sending changes to all users.")
                clientList.forEach {
                    val resp = Message(Operations.PUSH, transformations)
                    it.write(Json.encodeToString(resp))
                }
            } catch (ex: Exception) {
                println("Could not send message to other clients $ex")
            }
        }

        private fun requestChanges(clientList: MutableSet<ClientHandler>) {
            try {
                println("Requesting current transformations from all clients...")
                clientList.forEach {
                    if(it.clientSocket != clientSocket) {
                        val request = Message(Operations.PULL, "")
                        it.write(Json.encodeToString(request))
                    }
                }
            } catch (ex: Exception) {
                println("ERROR $ex")
            }
        }

        private fun applyChanges(transformations: JsonArray) {
            try {
                val trans = transformations.map { json ->
                    (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(project)
                }
                applyTransformationsTo(project, trans.toSet())
                project.saveProjectTo(Path(project.getPrivatePath())) // TODO as vezes da um erro :  I am not a child of my parent.

            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
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

    init {
        loadFiles()
        clientList = ArrayList()
        val serverSocket = ServerSocket(port)
        println("Server started on port $port")
        while (true) {
            val clientSocket = serverSocket.accept()
            println("Client connected: ${clientSocket.inetAddress.hostAddress}")
            val client = ClientHandler(clientSocket)
            clientsInfo[client] = JsonArray(emptyList()) // certo?
            clientList.add(client) // considera sempre que os clients sao novos, a lista esta em memoria neste momento
            thread {  client.run() }
        }
    }

}


