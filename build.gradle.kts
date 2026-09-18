plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "pt.iscte"
version = "0.2"

val mac = System.getProperty("os.name").lowercase().contains("mac")
val win = System.getProperty("os.name").lowercase().contains("windows")

val os = if (mac)
    "macos"
else if (win)
    "windows"
else
    "linux"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
    if(win)
        implementation(files("libs/javardise-windows-1.2.1.jar"))
    else
        implementation(files("libs/javardise-macos-1.2.1.jar"))
   // implementation(files("libs/compilation-1.2.0.jar"))
    implementation(files("libs/jaid-1.1.jar"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
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
    jvmToolchain(23)
}

tasks {
    register<Jar>("fatJar") {
        group = "distribution"
        //archiveFileName.set("javardair.jar")
        destinationDirectory.set(layout.buildDirectory.dir("dist"))
        dependsOn.addAll(
            listOf(
                "compileJava",
                "compileKotlin",
                "processResources"
            )
        )
        archiveClassifier.set(os)

        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest { attributes(mapOf("Main-Class" to "pt.iscte.javardair.server.ServerKt")) }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .filter { !it.name.contains("junit") && !it.name.contains("opentest") }
            .map { if (it.isDirectory) it else zipTree(it) } + sourcesMain.output
        from(contents) {
            exclude("**/*.RSA","**/*.SF","**/*.DSA")
        }
    }
}