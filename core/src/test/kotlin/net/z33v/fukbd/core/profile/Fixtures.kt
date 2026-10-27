package net.z33v.fukbd.core.profile

import java.io.File

/** Test data shared by the profile tests. Gradle runs tests with the :core module as working directory. */
object Fixtures {
    val root = File("src/test/resources/fixtures")
    val schema = File("../schema/device-profile-v2.1.schema.json")
    val examples = File("../profiles/examples")

    fun dir(name: String): List<File> =
        (File(root, name).listFiles { f -> f.name.endsWith(".json") } ?: error("no fixture dir $name")).sortedBy { it.name }

    fun text(f: File) = f.readText(Charsets.UTF_8)

    fun text(dir: String, name: String) = text(File(File(root, dir), name))

    val exampleFiles: List<File>
        get() = (examples.listFiles { f -> f.name.endsWith(".json") } ?: error("no examples")).sortedBy { it.name }
}
