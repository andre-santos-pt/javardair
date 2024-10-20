package pt.iscte.javardise.demo

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


    private fun applyChanges(editor: CodeEditor) {
        when(editor.folder) {
            workspaceOne -> editWorkspaceOne(editor)
            workspaceTwo -> editWorkspaceTwo(editor)
            workspaceThree -> editWorkspaceThree(editor)
        }
    }

    private fun editWorkspaceOne(editor: CodeEditor) {
        thread {

            // Add a new method and field in this workspace
            type.addMethod("newMethodWorkspaceOne", "void", "System.out.println(\"Hello from workspace one\");")
            sleep(1000)
            type.addField("int", "counter", "0", listOf(Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC))
            sleep(10000)
            // Delete a method that may be modified in workspace two
            type.deleteMethod("method")
        }
    }

    private fun editWorkspaceTwo(editor: CodeEditor) {
        thread {
            // This workspace will first add a method, then rename it, and eventually modify it
            val m = type.addMethod("method", "void", "System.out.println(\"Initial method\");")
            sleep(10000)
            m.rename("newNameMethod") // Rename method
            sleep(10000)
            m.addParam("int", "b") // Add a parameter
            sleep(10000)
            m.addStatement("System.out.println(b);") // Add a statement using the parameter
            // This should show how the other workspace deleting this method leads to a conflict
        }
    }

    private fun editWorkspaceThree(editor: CodeEditor) {
        thread {
            // Modify an existing method 'test' (assume it's already present)
            val m = type.getMethodsByName("test")[0]
            sleep(10000)
            m.addParam("int", "x") // Add a parameter to the 'test' method
            sleep(10000)
            m.addStatement("String conflito = \"0\";") // Add a new statement
            sleep(10000)
            // Add a new field with the same name as the one added in workspace one to cause a conflict
            type.addField("int", "counter", "5", listOf(Modifier.Keyword.PUBLIC))
        }

    }

    /**
     * Testar tambem com apagar coisas quando estao a ser usadas
     * adicionar rename
     */

    override fun run(editor: CodeEditor, toggle: Boolean) {
        applyChanges(editor)
    }
}