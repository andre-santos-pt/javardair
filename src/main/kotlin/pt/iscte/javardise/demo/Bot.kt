package pt.iscte.javardise.demo

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.SimpleName
import org.eclipse.swt.widgets.Display
import pt.iscte.javardise.CommandStack
import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import java.io.File
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class Bot : Action {
    override val name: String
        get() = "Bot"

    lateinit var commands: CommandStack
    lateinit var type: TypeDeclaration<*>

    private var workspaceOne = File("workspace_client1")
    private var workspaceTwo = File("workspace_client2")
    private var workspaceThree = File("workspace_client3")

    override fun init(editor: CodeEditor) {
        commands = editor.allUnitWidgets().first().commandStack // isto em vez de allClassWidgets
        type = editor.allCompilationUnits().first().types.first.get()
    }

    fun TypeDeclaration<*>.addMethod(name: String, retType: String): MethodDeclaration {
        val m = MethodDeclaration().apply {
            setName(name)
            setType(retType)
        }

        Display.getDefault().syncExec {
            commands.addCommand(members, this, m)
        }
        return m
    }

    fun MethodDeclaration.rename(newName: String) =
        Display.getDefault().syncExec {
            commands.modifyCommand(this, this.name, SimpleName(newName), this::setName)
        }

    fun MethodDeclaration.addParam(type: String, name: String) =
        Display.getDefault().syncExec {
            commands.addCommand(this.parameters, this, Parameter(StaticJavaParser.parseType(type), name))
        }

    fun MethodDeclaration.addStatement(src: String) =
        Display.getDefault().syncExec {
            commands.addCommand(body.get().statements, body.get(), StaticJavaParser.parseStatement(src))
        }

    private fun applyChanges(workspace: File, editor: CodeEditor) {
        when(workspace) {
            workspaceOne -> editWorkspaceOne()
            workspaceTwo -> editWorkspaceTwo()
            workspaceThree -> editWorkspaceThree()
        }
    }

    private fun editWorkspaceOne() {
        thread {
            val m = type.getMethodsByName("test")[0]
            sleep(2000)
            m.addStatement("String teste = \"Teste\";")
            sleep(2000)
            m.addStatement("return \"Hello\";")
        }
    }

    private fun editWorkspaceTwo() {
        thread {
            val m = type.getMethodsByName("method")[0]
            sleep(2000)
            m.rename("newNameMethod")
            sleep(2000)
            m.addParam("int", "b")
        }
    }

    private fun editWorkspaceThree() {
        thread {
            val m = type.getMethodsByName("test")[0]
            sleep(2000)
            m.addParam("int", "x")
            sleep(3000)
            m.addStatement("String conflito = \"0\";")

        }

    }

    /**
     * Testar tambem com apagar coisas quando estao a ser usadas
     */

    override fun run(editor: CodeEditor, toggle: Boolean) {
        applyChanges(workspaceOne, editor)
        applyChanges(workspaceTwo, editor)
        applyChanges(workspaceThree, editor)

    }
}