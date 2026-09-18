package pt.iscte.javardair.client

import java.io.File
import java.io.FileInputStream
import java.util.*

object ClientProperties {
    const val TRUNK_FOLDER: String = ".trunk"
    const val CONF_FILE: String = ".javardair"
    const val SERVER_PROP: String = "SERVER"
    const val PORT_PROP: String = "PORT"
    const val CLIENTID_PROP: String = "CLIENT-ID"
    const val DEBUG_PROP: String = "DEBUG"

    var address: String = "localhost"
    var port: Int = 8080
    var clientName: String = "client-${this.hashCode() % 100}"
    var debug: Boolean = false

    fun load(workingDir: String) {
        val props = Properties()
        try {
            FileInputStream(workingDir + File.separator + CONF_FILE).use { fis ->
                props.load(fis)
            }
        } catch (e: Exception) {
            println("Could not load properties file: ${e.message}")
        }
        address = props.getProperty(SERVER_PROP) ?: "localhost"
        port = props.getProperty(PORT_PROP)?.toInt() ?: 8080
        clientName = props.getProperty(CLIENTID_PROP)
            ?: "client-${this.hashCode() % 100}"
        debug = props.getProperty(DEBUG_PROP) == "true"
    }
}