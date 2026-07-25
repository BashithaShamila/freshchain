// Deliberately not a Spring module. These are the wire contracts between
// services; keeping the jar free of Spring and JPA means a consumer cannot
// accidentally couple itself to a producer's runtime.
plugins {
    `java-library`
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-annotations")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
