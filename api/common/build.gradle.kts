plugins {
    `java-library`
    `maven-publish`
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.getByName<Jar>("jar") {
    archiveBaseName.set("lika-common")
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

}
