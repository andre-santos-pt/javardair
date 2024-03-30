import java.io.OutputStream
import java.net.Socket
import java.util.Scanner
import kotlin.concurrent.thread

object Client {
    private const val address: String = "localhost" // mudar
    private const val port: Int = 8080 // mudar
    private lateinit var socket: Socket
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    var isConnected = false

    fun open() {
        runClient()
    }
    private fun runClient() {
        try {
            connectToServer()
        } catch (ex: Exception) {
            println("Disconnected from server")
        }
    }

    private fun connectToServer() {
        socket = Socket(address, port)
        isConnected = true
        reader = Scanner(socket.getInputStream())
        writer = socket.getOutputStream()
        thread { dealWithServer() }
    }

    fun close() {
        socket.close()
        isConnected = false
    }

    fun write(message: List<String>) {
        if(isConnected) message.forEach{ writer.write(it.encodeToByteArray())}
    }

    // é okay lidar assim? ou devo criar uma inner classe que seja uma thread dps?
    private fun dealWithServer() {
        // TODO lidar com as mensagens que vai receber do servidor para fazer mudanças ao ficheiro
        try {
            while (isConnected)
                println(reader.nextLine())
        } catch (ex: Exception) {
            println("Disconnected from server")
        }



    }
}

