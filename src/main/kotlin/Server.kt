import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import model.Project
import pt.iscte.javardise.demo.toTransformation
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Scanner
import kotlin.concurrent.thread

fun main() {
    Server(8080)
}
class Server(port: Int) {
    lateinit var clientList: ArrayList<ClientHandler>
    private lateinit var project: Project
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
                val resp = Json.decodeFromString<Response>(message)
                println(" Received changes from $clientSocket: $resp")
                if(resp.op == Operations.PUSH) {
                    applyChanges(resp.trans)
                    propagateChanges(resp.trans, clientList)
                }
            }
        }
        private fun write(message: String) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }

        private fun propagateChanges(serializedTransformations: String, clientList: ArrayList<ClientHandler>) {
            try {
                clientList.forEach {
                    if(it.clientSocket != clientSocket) {
                        val resp = Response(Operations.PUSH, serializedTransformations)
                        it.write(Json.encodeToString(resp))
                    }
                }
            } catch (ex: Exception) {
                println("Could not send message to other clients $ex")
            }
        }
    }

    private fun writeFile(path: String, src:String) {
        // everytime the server is initiated it loads a new set of files - TESTING PURPOSES
        val file = File(path)
        Files.deleteIfExists(file.toPath())
        if(!Files.exists(file.toPath()))
            Files.createDirectories(file.parentFile.toPath());
        PrintWriter(file).use { out ->
            out.println(src)
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

    private fun applyChanges(serializedTransformations: String) {
        try {
            (Json.parseToJsonElement(serializedTransformations) as JsonObject).toTransformation(project).applyTransformation(project)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
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
            clientList.add(client) // considera sempre que os clients sao novos, a lista esta em memoria neste momento
            thread { client.run() }
        }
    }

}


