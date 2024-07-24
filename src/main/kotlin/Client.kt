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
import kotlin.concurrent.thread
import kotlin.io.path.Path
import kotlin.reflect.jvm.isAccessible

object Client {
    private const val address: String = "localhost" // mudar
    private const val port: Int = 8080 // mudar
    private lateinit var socket: Socket
    private lateinit var reader: Scanner
    private lateinit var writer: OutputStream
    internal lateinit var projectLocal: Project
    internal lateinit var projectRoot: Project
    var isConnected = false
    var conflictFree = true // sera que deve começar true ou false?
    private val conflictsMap: MutableMap<String, MutableList<ConflictInfo>> = mutableMapOf()

    fun open() {
        isConnected = true
        runClient()
    }

    private fun runClient() {
        try {
            connectToServer()
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
                        checkChanges(Json.decodeFromString(message.content))
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
    private fun checkChanges(forcedTrans: JsonArray) {
        println("in checkChanges")
        // TODO perceber se é preciso forçar sair do focus

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

        if(setsAreEqual(currentTrans, forcedTransSerialized)){
            // TODO VER SE ISTO ASSIM ESTA BEM, ESTA VERIFICÇAO É A UNICA COISA QUE PROTEGE O ERRO DO NO VALUE PRESENT
            applyChanges(forcedTrans)

        } else {
            val redundancyFreeSetOfTransformations = RedundancyFreeSetOfTransformations(forcedTransSerialized, currentTrans)
            var conflicts = getConflicts(projectLocal, redundancyFreeSetOfTransformations)

            // apply changes normally
            applyChanges(forcedTrans)

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

    fun setsAreEqual(set1: MutableSet<Transformation>, set2: MutableSet<Transformation>): Boolean {
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

    private fun applyChanges(serializedTransformations: JsonArray) {
        try {
            val transRoot = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectRoot)
            }
            val transLocal = serializedTransformations.map { json ->
                (Json.parseToJsonElement(json.toString()) as JsonObject).toTransformation(projectLocal)
            }
            Display.getDefault().syncExec {
                applyTransformationsTo(projectLocal, transLocal.toSet())
                //projectLocal.saveProjectTo(Path(projectLocal.getPrivatePath()))
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

    private fun notifyConflicts(conflicts: List<ConflictInfo>) {
        println("ConflictList recebida: $conflicts")

        if(conflicts.isEmpty()) {
            //conflictsMap.clear()
        } else {
            conflicts.forEach { conflictInfo ->
                val conflictedPair = conflictInfo.conflictedPair
                val conflictedNodeUUID = conflictInfo.conflictedNodeUUID

                // TODO MUDAR ISTO, NAO FAZ SENTIDO ESTAR A MANDAR UMA MENSAGEM A DIZER QUE NAO HA CONFLITO

                if(conflictsMap.containsKey(conflictedPair)) {
                    if(conflictInfo.conflictMessage == "No conflicts") {
                        conflictsMap[conflictedPair]?.clear()
                    } else {
                        val conflictList = conflictsMap[conflictedPair]
                        val existingConflictIndex = conflictList?.indexOfFirst { it.conflictedNodeUUID == conflictedNodeUUID }

                        if (existingConflictIndex != null && existingConflictIndex != -1) {
                            conflictList[existingConflictIndex] = conflictInfo
                        } else {
                            conflictList?.add(conflictInfo)
                        }
                    }

                } else {
                    conflictsMap[conflictInfo.conflictedPair] = mutableListOf(conflictInfo)
                }

            }
        }



        //TODO tornar isto numa janela que observa o hashmap
        for ((pair, conflictInfos) in conflictsMap) {
            println("Conflicted Pair: $pair")
            for (conflictInfo in conflictInfos) {
                println(" - $conflictInfo")
            }
        }
    }
}

