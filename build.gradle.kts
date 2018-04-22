import org.gradle.api.tasks.wrapper.Wrapper.DistributionType
import org.jenkinsci.gradle.plugins.jpi.JpiDeveloper
import org.jenkinsci.gradle.plugins.jpi.ServerTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "uk.co.aaronvaz"
version = "1.1.0"
description = "Jenkins plugin to automatically create releases on Github"

plugins {
    val kotlinVersion = "1.2.40"

    kotlin("jvm") version (kotlinVersion)
    kotlin("kapt") version (kotlinVersion)

    id("org.jenkins-ci.jpi") version ("0.26.0")

    jacoco
}

jenkinsPlugin {
    coreVersion = "2.73.1"
    displayName = "Github Release Helper Plugin"
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

    compile(kotlin("stdlib"))
    compile(kotlin("reflect"))

    jenkinsPlugins("com.coravy.hudson.plugins.github:github:1.29.0")
    jenkinsPlugins("org.jenkins-ci.plugins:github-api:1.90")
    jenkinsPlugins("org.jenkins-ci.plugins:token-macro:2.5")

    jenkinsTest("org.jenkins-ci.main:jenkins-test-harness:2.38")
    jenkinsTest("org.jenkins-ci.modules:instance-identity:2.1")
    jenkinsTest("org.jenkins-ci.plugins:matrix-project:1.13")
    jenkinsTest("org.jenkins-ci.plugins:credentials:2.1.16")
    jenkinsTest("org.jenkins-ci.plugins.workflow:workflow-step-api:2.14")
    jenkinsTest("org.jenkins-ci.plugins:scm-api:2.2.6")
    jenkinsTest("org.jenkins-ci.plugins:structs:1.14")

    testCompile("junit:junit:4.12")
    testCompile("com.nhaarman:mockito-kotlin:1.5.0")
    testCompile("org.assertj:assertj-core:3.9.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

task<Delete>("cleanUp") {
    delete("target")
}

tasks.findByName("clean")!!.dependsOn("cleanUp")

task<Wrapper>("wrapper") {
    gradleVersion = "4.6"
}


