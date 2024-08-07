import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import messages.ClientMessage
import messages.ClientOperations
import messages.ServerMessage
import messages.ServerOperations
import model.*
import model.detachRedundantTransformations.RedundancyFreeSetOfTransformations
import model.transformations.Transformation
import org.eclipse.swt.widgets.Display
import pt.iscte.javardise.demo.toJson
import pt.iscte.javardise.demo.toTransformation
import java.io.File
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.Charset
import java.util.Scanner
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.reflect.jvm.isAccessible

object  Client {
    private const val address: String = "localhost" // TODO mudar
    private const val port: Int = 8080 // TODO mudar
    private lateinit var socket: Socket
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    internal lateinit var projectLocal: Project
    internal lateinit var projectRoot: Project
    var isConnected = false
    var conflictFree = true
    private lateinit var conflictsMap: ObservableConflictMap
    private lateinit var conflictView: ConflictView
    private val clientID: UUID = UUID.randomUUID() // TODO sera que este uuid devia ser criado quando a ide é aberta e nao quando o cliente se junta?

    fun open() {
        isConnected = true
        conflictsMap = ObservableConflictMap(mutableMapOf())
        conflictView = ConflictView()
        runClient()
    }

    private fun runClient() {
        try {
            println("Client $clientID")
            connectToServer()
            conflictsMap.addObserver(conflictView)
        } catch (ex: Exception) {
            println("Cannot connect to the server ${ex.printStackTrace()}")
        }
    }

    private fun connectToServer() {
        socket = Socket(address, port)
        reader = Scanner(socket.getInputStream())
        writer = socket.getOutputStream()
        thread {
            //requestRootFile()
            sendClientInfo()
            dealWithServer()
        }
    }

    fun close() {
        socket.close()
        isConnected = false
    }

    private fun dealWithServer() {
        try {
            while (isConnected) {
                val text = reader.nextLine()
                // The client will only receive messages from the server
                val message = Json.decodeFromString<ServerMessage>(text)
                println("Received message: $message")
                when(message.op) {
                    ServerOperations.FETCH_RESPONSE -> TODO()

                    ServerOperations.PROPAGATE -> {
                        // forcar o focus a sair
                        // ver se ha conflitos com a current lista de trans
                        // se houver, guardar esta current list numa var extra
                        // aplicar as mudanças vindas do propagate
                        // aplicar as mundanças da current list
                        checkChanges(Json.decodeFromString(message.content), message.sender)
                        //applyChanges(Json.decodeFro"mString(message.content))
                    }

                    ServerOperations.NOTIFY_CONFLICTS -> {
                        notifyConflicts(Json.decodeFromString(message.content))
                    }
                }
            }
        } catch (ex: Exception) {
            println("Disconnected from server: ${ex.printStackTrace()}")
        }
    }

    // safe mechanism to deal with the case of user making a change while receiving a PROPAGATE message
    private fun checkChanges(forcedTrans: JsonArray, sender: String) {
        println("in checkChanges")

        // get current changes
        val currentTrans = mutableSetOf<Transformation>()
        val factoryOfTransformations = FactoryOfTransformations(projectRoot, projectLocal)
        currentTrans.addAll(factoryOfTransformations.getListOfAllTransformations())
        currentTrans.forEach { println("currentTrans: ${it.toJson()}") }


        // check if conflicts exist between current changes and trans being forced into
        val forcedTransSerialized = forcedTrans.map { json ->
            (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectLocal)
        }.toMutableSet()
        forcedTransSerialized.forEach { println("forcedTrans: ${it.toJson()}") }


        // TODO talvez antes de fazer a verificaçao, aplicar a trans que veio ao root
        if(setsAreEqual(currentTrans, forcedTransSerialized)){
            // TODO VER SE ISTO ASSIM ESTA BEM, ESTA VERIFICÇAO É A UNICA COISA QUE PROTEGE O ERRO DO NO VALUE PRESENT
            applyChanges(forcedTrans, sender)

        } else {
            val redundancyFreeSetOfTransformations = RedundancyFreeSetOfTransformations(forcedTransSerialized, currentTrans)
            var conflicts = getConflicts(projectLocal, redundancyFreeSetOfTransformations)

            // apply changes normally
            applyChanges(forcedTrans, sender)

            // apply the current changes to the local only
            if(conflicts.isNotEmpty()) {
                // TODO Verificar se ele depois vai ver as difs bem
                Display.getDefault().syncExec {
                    applyTransformationsTo(projectLocal, currentTrans.toSet())
                    //projectLocal.saveProjectTo(Path(projectLocal.getPrivatePath()))
                }
            }
        }
        // TODO ERRO DIZ NO VALUE PRESENT so no client que faz o submit da mudança

    }

    private fun setsAreEqual(set1: MutableSet<Transformation>, set2: MutableSet<Transformation>): Boolean {
        if (set1.size != set2.size) return false

        val list1 = set1.map { it.toJson().toString() }.sorted()
        println(list1)
        val list2 = set2.map { it.toJson().toString() }.sorted()
        println(list2)

        return list1 == list2
    }

    fun write(message: String) {
        if(isConnected) {
            writer.write((message + '\n').toByteArray(Charset.defaultCharset()))
        }
    }

    // temp hack
    fun Project.getPrivatePath(): String {
        val f = this::class.members.find { it.name == "path" }
        f!!.isAccessible = true
        return f.call(this).toString()
    }

    private fun applyChanges(serializedTransformations: JsonArray, sender: String) {
        try {
            val transRoot = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectRoot)
            }
            val transLocal = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectLocal)
            }
            if(sender != clientID.toString()) {
                Display.getDefault().syncExec {
                    applyTransformationsTo(projectLocal, transLocal.toSet())
                    //projectLocal.saveProjectTo(Path(projectLocal.getPrivatePath()))
                }
            }
            applyTransformationsTo(projectRoot, transRoot.toSet())
            projectRoot.saveProjectTo(Path(projectRoot.getPrivatePath()))
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

    }

    private fun updateRootFile(file: String) {
        try {
            File(projectRoot.getProjectRoot().root.toString() + "\\Test.java").writeText(file) //TODO considera a trans de AddFile
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun requestRootFile() {
       if(isConnected) {
           val message = ClientMessage(ClientOperations.FETCH_REQUEST, "")
           write(Json.encodeToString(message))
       }
    }

    private fun notifyConflicts(conflicts: MutableMap<String, Set<ConflictInfo>>) {
        println("Hashmap recebido: $conflicts")

        conflicts.forEach { (client, conflictSet) ->
            conflictsMap[client] = conflictSet.toMutableList()
            conflictsMap.notifyObservers()
        }

        conflictFree = conflictsMap.all { it.value.isEmpty() } //TODO em vez de dar update aqui da variavel podia so fzr esta verifiaçao quando vou fazer submit

        //TODO tornar isto numa janela que observa o hashmap
        for ((pair, conflictInfos) in conflictsMap) {
            println("Conflicted Pair: $pair")
            for (conflictInfo in conflictInfos) {
                println(" - $conflictInfo")
            }
        }
    }

    private fun sendClientInfo() {
        // TODO Talvez criar um UUID para os clientes, para nao estar a usar os portes
        val message = ClientMessage(
            ClientOperations.HANDSHAKE,
            clientID.toString()
            //Json.encodeToString(clientID)
        )
        write(Json.encodeToString(message))
    }
}

