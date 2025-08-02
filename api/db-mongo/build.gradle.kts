apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "io.spring.dependency-management")


tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.named<Copy>("processTestResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}


tasks.getByName<Jar>("jar") {
    enabled = true
}

configurations.all {
    // Check for updates every build
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation(project(":common"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("io.github.microutils:kotlin-logging-jvm:${Versions.kotlinLogging}")


}
