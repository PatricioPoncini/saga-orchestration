import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("saga.kotlin-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":messaging"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}
