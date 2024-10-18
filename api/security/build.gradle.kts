apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "io.spring.dependency-management")
apply(plugin = "org.springframework.boot")

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    implementation(project(":common"))
    implementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${Versions.jackson}")
    implementation("io.github.microutils:kotlin-logging-jvm:${Versions.kotlinLogging}")
    implementation("com.auth0:java-jwt:${Versions.javaJwt}")
    implementation("com.github.ben-manes.caffeine:caffeine:${Versions.caffeine}")
    implementation("org.springframework.boot:spring-boot-starter-security")
    api("jakarta.servlet:jakarta.servlet-api")
    implementation("io.projectreactor:reactor-core:${Versions.reactor}")
    implementation("io.projectreactor.addons:reactor-adapter:${Versions.reactorAddons}")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:${Versions.reactorKotlinExt}")

}
