import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    extra["nextVersion"] = "major"
}

plugins {
    `java-gradle-plugin`
    groovy
    `kotlin-dsl`
    idea
    jacoco
    id("com.gradle.plugin-publish") version "1.0.0-rc-3"
    id("com.cinnober.gradle.semver-git") version "3.0.0"
    id("org.jetbrains.dokka") version "1.7.10"
    id("org.gradle.test-retry") version "1.5.0"
    `maven-publish`
}

group = "com.github.node-gradle"

val compatibilityVersion = JavaVersion.VERSION_1_8

java {
    sourceCompatibility = compatibilityVersion
    targetCompatibility = compatibilityVersion
}

tasks.compileKotlin {
    kotlinOptions {
        apiVersion = "1.3"
        freeCompilerArgs = listOf("-Xno-optimized-callable-references")
        jvmTarget = compatibilityVersion.toString()
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.14.2")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("org.assertj:assertj-core:3.24.2")
    testImplementation("cglib:cglib-nodep:3.3.0")
    testImplementation("org.objenesis:objenesis:3.3")
    testImplementation("commons-io:commons-io:2.13.0")
    testImplementation(platform("org.spockframework:spock-bom:2.3-groovy-3.0"))
    testImplementation("org.spockframework:spock-core")
    testImplementation("org.spockframework:spock-junit4")
    testImplementation("com.github.stefanbirkner:system-rules:1.19.0")
    testImplementation("org.mock-server:mockserver-netty:5.15.0")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.github.node-gradle"
            artifactId = "gradle-node-plugin"
            version = project.version.toString()
            pom {
                name.set("Gradle Node.js Plugin")
                description.set("Gradle plugin for executing Node.js scripts. Supports npm, pnpm, Yarn, and Bun.")
                url.set("https://github.com/node-gradle/gradle-node-plugin")
                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "localMaven"
            url = uri("${buildDir}/maven-repo")
        }
    }
}


tasks.compileTestGroovy {
    // Should be
    // classpath += files(sourceSets.test.get().kotlin.classesDirectory)
    // but unable to get it compile in the Kotlin DSL - works in the Groovy DSL as this
    // classpath += files(sourceSets.test.kotlin.classesDirectory)
    // This workaround works
    classpath += files("${buildDir}/classes/kotlin/test")
}

tasks.withType(Test::class) {
    useJUnitPlatform()
    systemProperty(
        com.github.gradle.buildlogic.GradleVersionsCommandLineArgumentProvider.PROPERTY_NAME,
        project.findProperty("testedGradleVersion") ?: gradle.gradleVersion
    )

    val processorsCount = Runtime.getRuntime().availableProcessors()
    val safeMaxForks = if (processorsCount > 2) processorsCount.div(2) else processorsCount
    maxParallelForks = safeMaxForks
    testLogging {
        events = setOf(TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        exceptionFormat = TestExceptionFormat.FULL
    }

    retry {
        maxRetries.set(3)
        filter {
            includeClasses.add("*_integTest")
        }
    }

    distribution {
        enabled.set(project.properties["com.github.gradle.node.testdistribution"].toString().toBoolean())
        remoteExecutionPreferred.set(project.properties["com.github.gradle.node.preferremote"].toString().toBoolean())
        if (project.properties["com.github.gradle.node.remoteonly"].toString().toBoolean()) {
            maxLocalExecutors.set(0)
        }
    }

    predictiveSelection {
        enabled.set(project.properties["com.github.gradle.node.predictivetestselection"].toString().toBoolean())
    }
}

tasks.test {
    exclude("**/Pnpm*Test*")
}

tasks.register<Test>("unitTests") {
    exclude("**/*_integTest*")
}

tasks.register<Test>("integrationTests") {
    include("**/*_integTest*")
}

tasks.register<Test>("pnpmTests") {
    include("**/Pnpm*Test*")
}

tasks.register<Test>("testGradleReleases") {
    jvmArgumentProviders.add(
        com.github.gradle.buildlogic.GradleVersionsCommandLineArgumentProvider(
            com.github.gradle.buildlogic.GradleVersionData::getReleasedVersions
        )
    )
}

tasks.register("printVersions") {
    doLast {
        println(com.github.gradle.buildlogic.GradleVersionData::getReleasedVersions.invoke())
    }
}

tasks.register<Test>("testGradleNightlies") {
    jvmArgumentProviders.add(
        com.github.gradle.buildlogic.GradleVersionsCommandLineArgumentProvider(
            com.github.gradle.buildlogic.GradleVersionData::getNightlyVersions
        )
    )
}

tasks.register("runParameterTest", JavaExec::class.java) {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.github.gradle.node.util.PlatformHelperKt")
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            jdkVersion.set(compatibilityVersion.majorVersion.toInt())
        }
    }
}

gradlePlugin {
    plugins {
        register("nodePlugin") {
            id = "com.github.node-gradle.node"
            implementationClass = "com.github.gradle.node.NodePlugin"
            displayName = "Gradle Node.js Plugin"
            description = "Gradle plugin for executing Node.js scripts. Supports npm, pnpm, Yarn and Bun."
        }
    }
}

pluginBundle {
    website = "https://github.com/node-gradle/gradle-node-plugin"
    vcsUrl = "https://github.com/node-gradle/gradle-node-plugin"

    tags = listOf("java", "node", "node.js", "npm", "yarn", "pnpm", "bun")
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
}
publishing.publications.withType<MavenPublication>().configureEach {
    pom.licenses {
        license {
            name.set("Apache License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
}
