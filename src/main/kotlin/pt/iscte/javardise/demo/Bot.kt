package pt.iscte.javardise.demo

import Client
import Client.getPrivatePath
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import messages.ClientMessage
import messages.ClientOperations
import model.FactoryOfTransformations
import model.UUID
import model.applyTransformationsTo
import model.transformations.BodyChangedCallable
import model.transformations.SignatureChanged
import model.transformations.Transformation
import org.eclipse.swt.widgets.Display
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.external.findMainClass
import pt.iscte.javardise.widgets.members.TYPE
import java.io.File
import java.util.Timer
import kotlin.concurrent.thread
import kotlin.io.path.Path

class Bot : Action {
    override val name: String
        get() = "Bot"
    private val transformationsClient2 = mutableSetOf<Transformation>()
    private val transformationsClient3 = mutableSetOf<Transformation>()
    override fun run(editor: CodeEditor, toggle: Boolean) {
        val transClient2 = mutableListOf<MutableSet<Transformation>>()
        val transClient3 = mutableListOf<MutableSet<Transformation>>()

        transClient2.add(mutableSetOf(BodyChangedCallable(Client.projectLocal, Client.projectLocal.getMethodByUUID(UUID("1b4fb7b4-07b7-480d-b63c-a5c58db73227"))!!, StaticJavaParser.parseBlock("{\n" +
                "    int b = a + 10;\n" +
                "    int x = 20;\n" +
                "}"))))
        transClient2.add(mutableSetOf(BodyChangedCallable(Client.projectLocal, Client.projectLocal.getMethodByUUID(UUID("ce2411ab-72e7-4c2b-b4b1-3e20dc187945"))!!, StaticJavaParser.parseBlock("{\n" +
                "        return \"Hello World! :)\";\n" +
                "    }"))))


        if(editor.folder == File("workspace_client2")) {
            editor.allCompilationUnits().forEach {
                transClient2.forEach {
                    transformationsClient2.clear()
                    println("A aplicar Transformaçao: $it")
                    Display.getDefault().syncExec {
                        applyTransformationsTo(Client.projectLocal, it)
                        Client.projectLocal.saveProjectTo(Path(Client.projectLocal.getPrivatePath()))
                        val factoryOfTransformations = FactoryOfTransformations(Client.projectRoot, Client.projectLocal)
                        transformationsClient2.addAll(factoryOfTransformations.getListOfAllTransformations())
                        println("transformations: $transformationsClient2")
                        val serializedTransformations = JsonArray(transformationsClient2.map { trans -> trans.toJson() })
                        try {
                            val message = ClientMessage(ClientOperations.UPDATE, Json.encodeToString(serializedTransformations))
                            println("Sending changes automatically: $message")
                            Client.write(Json.encodeToString(message))
                        } catch (ex: Exception) {
                            println("Could not send message to Server ${ex.printStackTrace()}")
                        }
                    }
                    Thread.sleep(10000)
                }
            }
        }
        if(editor.folder == File("workspace_client3")) {
            editor.allCompilationUnits().forEach {
                transClient3.forEach {
                    applyTransformationsTo(Client.projectLocal,it)
                    Thread.sleep(10000)
                }
            }
        }
    }
}