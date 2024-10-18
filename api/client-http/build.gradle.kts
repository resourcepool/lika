apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "io.spring.dependency-management")
apply(plugin = "org.springframework.boot")

java {
    withJavadocJar()
    withSourcesJar()
}

plugins {
    `java-library`
    `maven-publish`
}

tasks.named<Copy>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.named<Copy>("processTestResources") {
    duplicatesStrategy = DuplicatesStrategy.WARN
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    archiveBaseName.set("lika-client-http")
    duplicatesStrategy = DuplicatesStrategy.WARN
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            if(project.version.toString().endsWith("-SNAPSHOT")) {
                url = uri(System.getenv("NEXUS_REPOSITORY_SNAPSHOTS_URL") ?: System.getProperty("nexus.repository.snapshots.url"))
            } else {
                url = uri(System.getenv("NEXUS_REPOSITORY_RELEASES_URL") ?: System.getProperty("nexus.repository.releases.url"))
            }
            credentials {
                username = System.getenv("NEXUS_USER") ?: System.getProperty("nexus.user")
                password = System.getenv("NEXUS_PASSWORD") ?: System.getProperty("nexus.password")
            }
        }
        mavenLocal()
        mavenCentral()
    }
}

dependencies {

    implementation(project(":common"))
    implementation("io.reactivex.rxjava3:rxjava:${Versions.rxJava}")
    implementation("com.fasterxml.jackson.core:jackson-annotations:${Versions.jackson}")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.projectreactor:reactor-core:${Versions.reactor}")
    implementation("org.springframework:spring-webflux")
    implementation("org.springframework.boot:spring-boot")
    implementation("io.netty:netty-all")
    implementation("io.projectreactor.netty:reactor-netty:${Versions.reactorNetty}")
    implementation("io.github.microutils:kotlin-logging-jvm:${Versions.kotlinLogging}")
    testImplementation("io.resourcepool:little-bob:${Versions.littleBob}")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("at.favre.lib:bcrypt:${Versions.bcrypt}")

}
