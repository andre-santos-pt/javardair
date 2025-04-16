package pt.iscte.javardair.actions

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
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

    private fun TypeDeclaration<*>.addMethod(name: String, retType: String, body: String? = null): MethodDeclaration {
        val m = MethodDeclaration().apply {
            setName(name)
            setType(retType)
            if(body != null) createBody().addStatement(body)
        }
        Display.getDefault().syncExec {
            commands.addCommand(members, this, m, 0)
        }
        return m
    }

    private fun TypeDeclaration<*>.addField(type: String, name: String, initializer: String? = null, modifiers: List<Modifier.Keyword>? = listOf(Modifier.Keyword.PRIVATE)) : FieldDeclaration {
        val f = FieldDeclaration().apply {
            val variable = VariableDeclarator().apply {
                this.type = StaticJavaParser.parseType(type)
                this.name = SimpleName(name)
            }
            if (initializer != null) {
                variable.setInitializer(StaticJavaParser.parseExpression(initializer))
            }
            addVariable(variable)
            modifiers?.forEach { addModifier(it) }
        }
        Display.getDefault().syncExec {
            commands.addCommand(members, this, f, 0)
        }
        return f
    }

    private fun TypeDeclaration<*>.deleteMethod(methodName: String) {
        val m = this.getMethodsByName(methodName).firstOrNull()
        m?.let {
            Display.getDefault().syncExec {
                commands.removeCommand(this.members,this, it)
            }
        }
    }

    private fun TypeDeclaration<*>.deleteField(fieldName: String) {
        val f = this.fields.firstOrNull { it.variables[0].nameAsString == fieldName }
        f?.let {
            Display.getDefault().syncExec {
                commands.removeCommand(this.members, this, it)
            }
        }
    }

    private fun MethodDeclaration.rename(newName: String) =
        Display.getDefault().syncExec {
            commands.modifyCommand(this, this.name, SimpleName(newName), this::setName)
        }

    private fun MethodDeclaration.addParam(type: String, name: String) =
        Display.getDefault().syncExec {
            commands.addCommand(this.parameters, this, Parameter(StaticJavaParser.parseType(type), name))
        }

    private fun MethodDeclaration.addStatement(src: String) =
        Display.getDefault().syncExec {
            commands.addCommand(body.get().statements, body.get(), StaticJavaParser.parseStatement(src))
        }


    /** fun FieldDeclaration.changeTypeField(type: String) {
        Display.getDefault().syncExec {
            commands.modifyCommand(this, this.variables[0], StaticJavaParser.parseType(type), this::setAllTypes)
        }
    }**/

    /**fun FieldDeclaration.changeNameField(name: String) {
        Display.getDefault().syncExec {
            val varDeclarator = this.variables[0]
            commands.modifyCommand(varDeclarator, varDeclarator.name, name, varDeclarator::setName)
        }
    }**/

    /**fun FieldDeclaration.changeInitalizerField(initializer: String) {
        Display.getDefault().syncExec {
            commands.modifyCommand()
        }
    }**/


    private fun applyChanges(editor: CodeEditor) {
        when(editor.folder) {
            workspaceOne -> editWorkspaceOne(editor)
            workspaceTwo -> editWorkspaceTwo(editor)
            workspaceThree -> editWorkspaceThree(editor)
        }
    }

    private fun editWorkspaceOne(editor: CodeEditor) {
        thread {
            val connectToServer = ConnectToServer()
            val push = Push()
            val forcePush = ForcePush()

            // Connecting to the server
            connectToServer.run(editor, true)

            sleep(15000)

            // Add a new method that calls "testMethod" -> SHOWS HOW IT DOESNT SHOW CONFLICT WITH CLIENT 2 EVEN THO ITS CALLING A METHOD THATS GONNA BE RENAMED BY CLIENT2 AND UPDATES REFERENCE
            val workspaceOneMethod = type.addMethod("worskpaceOneMethod", "String", "return testMethod(\"Hello World!\");")
            sleep(20000)
            push.run(editor, true)

            sleep(10000)

            // Change method "changableMethod" ->  WILL CHANGE METHOD AND SHOW CONFLICT WITH CLIENT3 USE THAT WILL ALSO CHANGE BODY
            val changableMethod = type.getMethodsByName("changableMethod")[0]
            changableMethod.addStatement("String b = \"Changed the body! Will cause conflict\";")

            sleep(20000)

            // Changes method "workspaceOneMethod" -> WILL SHOW CONFLICT WITH CLIENT2 BC IT TRIES TO DELETE
            workspaceOneMethod.addParam("int", "paramInt")



        }
    }

    private fun editWorkspaceTwo(editor: CodeEditor) {
        thread {
            val connectToServer = ConnectToServer()
            val push = Push()
            val forcePush = ForcePush()

            // Connecting to the server
            connectToServer.run(editor, true)

            sleep(10000)

            // Rename existent method "testMethod" and Push changes -> SHOWS HOW IT UPDATES ALL CALLS OF THE METHOD (EVEN WHEN CLIENT 1 IS TRYING TO CALL THE METHOD)
            val testMethod = type.getMethodsByName("testMethod")[0]
            testMethod.rename("renamedMethod")
            sleep(15000)
            push.run(editor, true)

            sleep(35000)

            // Deletes existent method "workspaceOneMethod" -> Shows conflict when CLIETN1 tries to edit the same method
            type.deleteMethod("workspaceOneMethod")



        }
    }

    private fun editWorkspaceThree(editor: CodeEditor) {
        thread {
            val connectToServer = ConnectToServer()
            val push = Push()
            val forcePush = ForcePush()

            // Connecting to the server
            connectToServer.run(editor, true)

            sleep(50000)

            // Change method "changableMethod" ->  WILL CHANGE METHOD AND SHOW CONFLICT WITH CLIENT 1 USE THAT WILL ALSO CHANGE BODY
            val changableMethod = type.getMethodsByName("changableMethod")[0]
            changableMethod.addStatement("int var = 10;")

            sleep(5000)

            forcePush.run(editor, true) // -> MOSTRA O FORCE PUSH


        }

    }

    override fun run(editor: CodeEditor, toggle: Boolean) {
        applyChanges(editor)
    }
}