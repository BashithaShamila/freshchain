plugins {
    // Applied at the root only so the Kotlin DSL below gets its type-safe
    // accessors (sourceSets, java {}); the root project itself has no sources.
    java
    jacoco
    id("org.springframework.boot") version "3.5.16" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

// No sources up here — without this, every build writes an empty
// freshchain-0.1.0.jar at the repository root.
tasks.jar { enabled = false }

allprojects {
    group = "com.freshchain"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.16")
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.3")
            mavenBom("org.springframework.ai:spring-ai-bom:1.0.9")
            mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.42")
        "annotationProcessor"("org.projectlombok:lombok:1.18.42")
        "testCompileOnly"("org.projectlombok:lombok:1.18.42")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.42")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        // Testcontainers needs a generous heap when Postgres + Kafka run side by side.
        maxHeapSize = "1g"
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    tasks.named<Test>("test") {
        useJUnitPlatform {
            // Chaos tests deliberately spend time in a broken state, so they are
            // opt-in rather than part of every build. Run them with `make chaos`.
            excludeTags("chaos")
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    // Deliberately not finalized by the coverage report: that report depends on
    // `test`, and wiring it here would make `chaosTest` drag the whole suite
    // along with it.
    tasks.register<Test>("chaosTest") {
        description = "Runs the chaos tests: broker outage, and recovery from it."
        group = "verification"
        testClassesDirs = sourceSets["test"].output.classesDirs
        classpath = sourceSets["test"].runtimeClasspath
        useJUnitPlatform { includeTags("chaos") }
        outputs.upToDateWhen { false }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
