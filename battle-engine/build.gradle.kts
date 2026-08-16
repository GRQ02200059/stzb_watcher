import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "1.9.23"
    application
}

dependencies {
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.0")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.stzb.battle.cli.BattleEngineCliKt")
}

tasks.test {
    useJUnitPlatform()
    val sourceManifest = JsonSlurper().parse(
        layout.projectDirectory.file("SOURCE.json").asFile,
    ) as Map<*, *>
    val knownSourceTestFailures =
        (sourceManifest["knownSourceTestFailures"] as? List<*>)
            .orEmpty()
            .filterIsInstance<Map<*, *>>()
    knownSourceTestFailures.forEach { failure ->
        filter.excludeTestsMatching(
            "${failure["targetClass"]}.${failure["method"]}",
        )
    }
}
