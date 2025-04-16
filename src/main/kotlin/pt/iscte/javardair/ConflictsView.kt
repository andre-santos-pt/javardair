package pt.iscte.javardair

import kotlinx.serialization.json.*
import pt.iscte.javardair.messages.ConflictInfo
import java.awt.Dimension
import javax.swing.*

interface ConflictObservable {
    fun notifyObservers()
    fun addObserver(o: ConflictsObserver?)
}
interface ConflictsObserver {
    fun update(map: Map<String, MutableList<ConflictInfo>>)}

class ObservableConflictMap(val map: MutableMap<String, MutableList<ConflictInfo>>) : MutableMap<String, MutableList<ConflictInfo>> by map,
    ConflictObservable {
    private val observers: MutableSet<ConflictsObserver> = mutableSetOf()

    override fun put(key: String, value: MutableList<ConflictInfo>): MutableList<ConflictInfo>? {
        val result = map.put(key, value)
        notifyObservers()
        return result
    }

    override fun putAll(from: Map<out String, MutableList<ConflictInfo>>) {
        map.putAll(from)
        notifyObservers()
    }

    override fun remove(key: String): MutableList<ConflictInfo>? {
        val result = map.remove(key)
        notifyObservers()
        return result
    }

    override fun clear() {
        map.clear()
        notifyObservers()
    }

    override fun notifyObservers() {
        observers.forEach { it.update(this.map) }
    }

    override fun addObserver(o: ConflictsObserver?) {
        if (o != null) {
            observers.add(o)
        }
    }
}

class ConflictView : JFrame("Conflict Viewer"), ConflictsObserver {
    private val textArea = JTextArea()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(400, 300)
        layout = BoxLayout(contentPane, BoxLayout.Y_AXIS)

        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true

        add(JScrollPane(textArea))
        setLocationAtBottomRight()
        showView()
    }

    private fun setLocationAtBottomRight() {
        val owner = this.owner
        if (owner is JFrame) {
            val ownerBounds = owner.bounds
            val x = ownerBounds.x + ownerBounds.width - width - 10
            val y = ownerBounds.y + ownerBounds.height - height - 10
            setLocation(x, y)
        } else {
            setLocationRelativeTo(null)
        }
    }

    private fun appendConflictList(conflictMap: Map<String, MutableList<ConflictInfo>>) {
        textArea.text = ""
        for ((pair, conflicts) in conflictMap) {

            textArea.append("Pair: ${pair.split(",")[1]}\n")
            conflicts.forEach { conflict ->
                var conflictedTransformation = conflict.conflictedTransformation
                textArea.append("\nConflict Detected:\n")
                textArea.append("   - Description: ${conflict.conflictMessage.trim('"')}\n")
                textArea.append("   - Conflicting Transformation: ${conflict.conflictTransformationMessage}\n")
                val relevantInfo = getRelevantInfo(conflictedTransformation)
                if(relevantInfo.isNotEmpty()) {
                    textArea.append("   - Additional Information:\n")
                    relevantInfo.forEach{ (key, value) ->
                        textArea.append("       $key: $value\n")
                    }
                }
            }
            textArea.append("------------------------------------------------------\n\n")
        }
    }

    // Customizable Transformation Info View
    private fun getRelevantInfo(transformation: JsonObject): Map<String, String> {
        return when (transformation["code"].toString().trim('"')) {
            /**
            "SignatureChanged" -> {
                mapOf(
                    "Changed Method Name to" to transformation["name"].toString(),
                    "Changed Parameters to" to transformation["parameters"].toString()
                )
            }
            **/

            "AddCallable" -> {
                mapOf(
                    "Constructor of the method added" to transformation["constructor"].toString(),
                    "Body of the method added" to transformation["body"].toString()
                )
            }

            /**
            "ReturnTypeChangedMethod" -> {
                mapOf(
                    "Changed Return Type to" to transformation["returnType"].toString()
                )
            }**/

            "BodyChangedCallable" -> {
                mapOf(
                    "Changed body to" to transformation["body"].toString()
                )
            }

            /**
            "RenameField" -> {
                mapOf(
                    "Changed Field Name to" to transformation["name"].toString()
                )
            }

            "TypeChangedField" -> {
                mapOf(
                    "Changed Field Type to" to transformation["type"].toString()
                )
            }

            "InitializerChangedField" -> {
                mapOf(
                    "Changed Field Initalizer to" to transformation["initializer"].toString()
                )
            }

            "RemoveCallable", "MoveCallableIntraType", "AddField", "RemoveField" -> {
                emptyMap()
            }
            **/
            else -> emptyMap()
        }
    }

    private fun showView() {
        isVisible = true
    }

    override fun update(map: Map<String, MutableList<ConflictInfo>>) {
        appendConflictList(map)
        revalidate()
        repaint()
    }
}