import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.Charset
import java.util.Scanner
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val server = ServerSocket(8080)
    println("Server is running on port ${server.localPort}")

    while (true) {
        val client = server.accept()
        println("Client connected: ${client.inetAddress.hostAddress}")
        thread { ClientHandler(client).run() }
    }
}

class ClientHandler(client: Socket) {
    private val client: Socket = client
    private val writer: OutputStream = client.getOutputStream()
    private val reader: Scanner = Scanner(client.getInputStream())
    private  var running: Boolean = false

    fun run() {
        running = true

        write("Welcome to the server")

        while (running) {
            try {
                val text = reader.nextLine()
                println(text)
            } catch (ex: Exception) {
                println(ex)
                shutdown()
            } finally {
                // TODO
            }
        }
    }

    private fun write(message: String) {
        writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
    }

    private fun shutdown() {
        running = false
        client.close()
        println("${client.inetAddress.hostAddress} closed the connection")
    }
}