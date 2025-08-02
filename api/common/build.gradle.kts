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
        mavenLocal()
        mavenCentral()
    }
}

dependencies {

}
