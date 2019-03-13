import org.jenkinsci.gradle.plugins.jpi.JpiDeveloper

group = "uk.co.aaronvaz"
version = "1.3.0"
description = "Jenkins plugin to automatically create releases on GitHub"

plugins {
    val kotlinVersion = "1.3.21"

    kotlin("jvm") version (kotlinVersion)
    kotlin("kapt") version (kotlinVersion)

    id("org.jenkins-ci.jpi") version ("0.29.0")

    jacoco
}

jenkinsPlugin {
    coreVersion = "2.73.1"
    displayName = "GitHub Release Helper Plugin"
    shortName = "github-release-helper"
    workDir = file("$buildDir/work")

    developers.apply {
        developer(delegateClosureOf<JpiDeveloper> {
            setProperty("id", "aaron-vaz")
            setProperty("name", "Aaron Vaz")
            setProperty("email", "vazaaron08@gmail.com")
        })
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // SezPoz is used to process @hudson.Extension and other annotations
    kapt("net.java.sezpoz:sezpoz:1.12")

    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.squareup.okhttp:logging-interceptor:2.7.5")

    jenkinsPlugins("com.coravy.hudson.plugins.github:github:1.27.0")
    jenkinsPlugins("org.jenkins-ci.plugins:github-api:1.85.1")
    jenkinsPlugins("org.jenkins-ci.plugins:token-macro:2.1")

    jenkinsTest("org.jenkins-ci.main:jenkins-test-harness:2.38")
    jenkinsTest("org.jenkins-ci.modules:instance-identity:2.1")
    jenkinsTest("org.jenkins-ci.plugins:matrix-project:1.13")
    jenkinsTest("org.jenkins-ci.plugins:credentials:2.1.16")
    jenkinsTest("org.jenkins-ci.plugins.workflow:workflow-step-api:2.14")
    jenkinsTest("org.jenkins-ci.plugins:scm-api:2.2.6")
    jenkinsTest("org.jenkins-ci.plugins:structs:1.14")

    testImplementation("junit:junit:4.12")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.1.0")
    testImplementation("com.squareup.okhttp:mockwebserver:2.7.5")
}

task<Delete>("cleanUp") {
    delete("target")
}

task("ci") {
    group = LifecycleBasePlugin.BUILD_GROUP
    dependsOn("build")
    doLast {
        file("$buildDir/version.txt").writeText(version.toString(), Charsets.UTF_8)
    }
}

tasks {
    compileKotlin {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }

    test {
        finalizedBy("jacocoTestReport")
    }

    clean {
        dependsOn("cleanUp")
    }

    wrapper {
        gradleVersion = "5.2.1"
    }
}

