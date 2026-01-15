import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("org.springframework.boot") version "${Versions.springBoot}"
    id("io.spring.dependency-management") version "${PluginVersions.springDependencyMgmt}"
    kotlin("jvm") version "${PluginVersions.kotlinJvm}"
    kotlin("plugin.spring") version "${PluginVersions.kotlinSpring}"
}

tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.getByName<Jar>("jar") {
    enabled = false
}

allprojects {
    group = "li.lika"
    version = "0.3.0"
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "io.spring.dependency-management")

    repositories {
        mavenCentral()
        mavenLocal()

    }

    dependencyManagement {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinReflect}")
        implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinStdLib}")
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }
}




subprojects {

    sourceSets {
        main {
            java.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")
        }

        test {
            java.srcDir("src/test/kotlin")
            resources.srcDir("src/test/resources")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
