package pt.iscte.javardair

import kotlin.reflect.jvm.isAccessible


fun Any.getPrivateField(name: String): Any? {
    val f = this::class.members.find { it.name == name }
    if(f == null)
        throw NoSuchFieldException("Field $name not found in ${this::class.simpleName}")
    else {
        f.isAccessible = true
        return f.call(this)
    }
}

//fun Project.getPrivatePath(): String {
//    val f = this::class.members.find { it.name == "path" }
//    f!!.isAccessible = true
//    return f.call(this).toString()
//}