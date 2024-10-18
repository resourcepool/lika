apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "io.spring.dependency-management")
apply(plugin = "org.springframework.boot")


configure<org.springframework.boot.gradle.dsl.SpringBootExtension> {
    buildInfo()
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.named<Copy>("processTestResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    // Workaround for issue https://github.com/spring-projects/spring-boot/issues/20999
    this.archiveFileName.set("${archiveBaseName.get()}.${archiveExtension.get()}")
    rootSpec.filesMatching("**/*.jar") {
        // only rename jars which are going into the lib dir into the fat jar
        if (path.startsWith("BOOT-INF/lib")) {
            // rename the destination from "module-version.jar" to "group-module-version.jar"
            val groupId = file.parentFile.parentFile.parentFile.parentFile.name
            name = "$groupId-${name}"
        }
    }
}

tasks.getByName<Jar>("jar") {
    enabled = false
}

configurations.all {
    // Check for updates every build
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

dependencies {
    implementation(project(":common"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("io.github.microutils:kotlin-logging-jvm:${Versions.kotlinLogging}")
    runtimeOnly("com.h2database:h2:${Versions.h2}")


}
