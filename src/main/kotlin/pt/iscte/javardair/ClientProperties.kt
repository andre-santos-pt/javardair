package pt.iscte.javardair

import java.io.File
import java.io.FileInputStream
import java.util.*

const val configurationFile: String = ".javardair"

const val serverProperty: String = "SERVER"
const val portProperty: String = "PORT"
const val clientProperty: String = "CLIENT-ID"

object ClientProperties {
    var address: String = "localhost"
    var port: Int = 8080
    var clientName: String = "client-${this.hashCode() % 100}"
    val trunkFolder: String = ".trunk"

    fun load(workingDir: String) {
        val props = Properties()
        try {
            FileInputStream(workingDir + File.separator + configurationFile).use { fis ->
                props.load(fis)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        println(props)
        address = props.getProperty(serverProperty) ?: "localhost"
        port = props.getProperty(portProperty)?.toInt() ?: 8080
        clientName = props.getProperty(clientProperty)
            ?: "client-${this.hashCode() % 100}"
    }
}