import kotlinx.serialization.Serializable

@Serializable
data class Message(val op: Operations, val trans: String) {
    //constructor(op: Operations, trans: String): this(op) // como acedo dps ao trans desta forma?
    //constructor(op: Operations, file:List<String>): this(op)
}