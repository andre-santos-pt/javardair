package pt.iscte.javardise.demo

import pt.iscte.javardise.editor.Action
import pt.iscte.javardise.editor.CodeEditor
import pt.iscte.javardise.external.findMainClass
import pt.iscte.javardise.widgets.members.TYPE
import java.io.File

class Bot : Action {
    override val name: String
        get() = "Bot"

    override fun run(editor: CodeEditor, toggle: Boolean) {
        if(editor.folder == File("workspace_client2")) {
            editor.allCompilationUnits().forEach {
                // TODO A lista de mudanças nao é atualizada aqui, talvez fazer o envio automatico/atualizaçao da lista aqui, so depois de fazer uma mudança manual
                // TODO Perceber como fazer alteraçoes em metodos
                it.getClassByName("Test").get().addField("int","varTeste")
            }
        }
    }
}