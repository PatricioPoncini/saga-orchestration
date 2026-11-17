plugins {
    id("saga.library-conventions")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-amqp")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-json")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")
    api("org.flywaydb:flyway-core")
    compileOnly("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
}
