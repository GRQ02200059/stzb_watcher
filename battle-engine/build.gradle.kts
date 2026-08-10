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
}
