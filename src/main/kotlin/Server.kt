import model.Project
import java.io.File
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.ArrayList
import java.util.Scanner
import kotlin.concurrent.thread

fun main() {
    Server(8080)
}
class Server(port: Int) {
    lateinit var clientList: ArrayList<ClientHandler>
    //val projBase = Project(File(editor.folder, "base").absolutePath.toString())
    inner class ClientHandler(clientSocket: Socket) {
        private val clientSocket: Socket = clientSocket
        private val writer: OutputStream = clientSocket.getOutputStream()
        private val reader: Scanner = Scanner(clientSocket.getInputStream())

        fun run() {
            write("Welcome to the server")
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
                val text = reader.nextLine()
                // TODO fazer as mudanças e propagar - fase 1
                clientList.forEach { if(it.clientSocket != clientSocket) it.write("from ${it}: $text") }
            }
        }
        private fun write(message: String) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
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
    }

    private fun loadFiles() {
        // branch version
        writeFile("temp/base/Test.java", """
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
        //clientList.forEach{ println(it) }
        //loadFiles()
        clientList = ArrayList<ClientHandler>()
        val serverSocket = ServerSocket(port)
        println("Server started on port $port")
        while (true) {
            val clientSocket = serverSocket.accept()
            println("Client connected: ${clientSocket.inetAddress.hostAddress}")
            val client = ClientHandler(clientSocket)
            clientList.add(client) // considera sempre que os clients sao novos, a lista esta em memoria neste momento
            thread { client.run() }
            //clientList.forEach { println(it) }
        }
    }

}


