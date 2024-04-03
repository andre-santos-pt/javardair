plugins {
    kotlin("jvm") version "1.9.22"
    application
    kotlin("plugin.serialization") version "1.9.22"
}

group = "pt.iscte"
version = "1.0-SNAPSHOT"

val win = System.getProperty("os.name").lowercase().contains("windows")


repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    if(win)
        implementation(files("libs/javardise-win.jar"))
    else
        implementation(files("libs/javardise-macos.jar"))
    implementation(files("libs/compilation.jar"))
    implementation(files("libs/jaid.jar"))
}

application {
    mainClass.set("pt.iscte.javardise.editor.MainKt")
    if(!win)
        applicationDefaultJvmArgs = listOf("-XstartOnFirstThread")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}