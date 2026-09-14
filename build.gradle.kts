plugins {
    kotlin("jvm") version "1.9.22"
    application
    kotlin("plugin.serialization") version "1.9.22"
}

group = "pt.iscte"
version = "1.0-SNAPSHOT"

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
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation(kotlin("reflect"))
    if(win)
        implementation(files("libs/javardise-win.jar"))
    else
        implementation(files("libs/javardise-macos-1.2.0.jar"))
    //implementation(files("libs/compilation.jar"))
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

tasks {
    register<Jar>("fatJar") {
        group = "distribution"
        archiveFileName.set("javardair.jar")
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
        manifest { attributes(mapOf("Main-Class" to "pt.iscte.javardair.ServerKt")) }
        val sourcesMain = sourceSets.main.get()
        val contents = configurations.runtimeClasspath.get()
            .filter { !it.name.contains("junit") && !it.name.contains("opentest") }
            .map { if (it.isDirectory) it else zipTree(it) } + sourcesMain.output
        from(contents) {
            exclude("**/*.RSA","**/*.SF","**/*.DSA")
        }
    }
}