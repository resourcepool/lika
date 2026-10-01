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
    implementation(project(":client-http"))
    implementation(project(":security"))

    if (project.hasProperty("dbMongo")) {
        runtimeOnly(project(":db-mongo"))
        println("Using MongoDB as database")
    } else if (project.hasProperty("dbSqlite")) {
        runtimeOnly(project(":db-sqlite"))
        println("Using SQLite as database")
    } else {
        //runtimeOnly(project(":db-sqlite"))
        runtimeOnly(project(":db-memory"))
        println("Using Memory as database")
    }
    implementation("com.fasterxml.jackson.core:jackson-core:${Versions.jackson}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${Versions.jackson}")
    implementation("com.fasterxml.jackson.core:jackson-databind:${Versions.jackson}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${Versions.jackson}")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework:spring-tx")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.github.microutils:kotlin-logging-jvm:${Versions.kotlinLogging}")
    implementation("com.github.ben-manes.caffeine:caffeine:${Versions.caffeine}")
    implementation("com.auth0:java-jwt:${Versions.javaJwt}")
    implementation("io.netty:netty-all")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${Versions.springdoc}")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.springframework.security:spring-security-test")
    // Tests run against the in-memory database
    testImplementation("org.springframework:spring-jdbc")

}
