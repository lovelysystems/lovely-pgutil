plugins {
    application
    id("com.lovelysystems.gradle") version ("1.3.2")
    kotlin("jvm") version "1.4.0"
}

group = "com.lovelysystems"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.github.microutils:kotlin-logging:1.8.3")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.testcontainers:testcontainers:1.15.0-rc1")
    implementation("com.github.ajalt:clikt:2.8.0")
    implementation("com.github.docker-java:docker-java:3.2.5")
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation(kotlin("test-junit5"))
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.1.3")
}

application {
    mainClassName = "CliKt"
    applicationName = "pgutil"
}

lovely {
    gitProject()
    dockerProject("lovelysystems/pgutil")
    dockerFiles.from(tasks["distTar"].outputs)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
// compile bytecode to java 8 (default is java 6)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
