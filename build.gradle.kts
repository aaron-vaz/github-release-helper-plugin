import org.gradle.api.tasks.wrapper.Wrapper.DistributionType
import org.jenkinsci.gradle.plugins.jpi.JpiDeveloper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "uk.co.aaronvaz"
version = "1.0.0-SNAPSHOT"
description = "Jenkins plugin to automatically create releases for git scm services"

plugins {
    kotlin("jvm", "1.1.51")
    kotlin("kapt")
    id("org.jenkins-ci.jpi") version ("0.22.0")
    `findbugs`
}

jenkinsPlugin {
    coreVersion = "2.19.4"
    displayName = "Github Release Helper Plugin"
    shortName = "github-release-helper"
    workDir = file("$buildDir/work")

    developers = this.Developers().apply {
        developer(delegateClosureOf<JpiDeveloper> {
            setProperty("id", "aaron-vaz")
            setProperty("name", "Aaron Vaz")
            setProperty("email", "vazaaron08@gmail.com")
        })
    }
}

findbugs {
    effort = "max"
    reportLevel = "high"
    isShowProgress = true
}


repositories {
    mavenCentral()
}

dependencies {
    // SezPoz is used to process @hudson.Extension and other annotations
    kapt("net.java.sezpoz:sezpoz:1.11")

    compile(kotlin("stdlib-jre8", "1.1.51"))
    compile("com.coravy.hudson.plugins.github:github:1.28.0")
    compile("org.jenkins-ci.plugins:github-api:1.89")

    jenkinsPlugins("com.coravy.hudson.plugins.github:github:1.28.0@jar")
    jenkinsPlugins("org.jenkins-ci.plugins:github-api:1.89@jar")

    jenkinsTest("org.jenkins-ci.plugins:matrix-project:1.7.1")
    jenkinsTest("org.jenkins-ci.main:jenkins-test-harness:2.22@jar")
    jenkinsTest("org.jenkins-ci.plugins:token-macro:2.3@jar")
    jenkinsTest("org.jenkins-ci.plugins:credentials:2.1.16@jar")
    jenkinsTest("org.jenkins-ci.plugins.workflow:workflow-step-api:2.13@jar")
    jenkinsTest("org.jenkins-ci.plugins:scm-api:2.2.2@jar")
    jenkinsTest("org.jenkins-ci.plugins:structs:1.10@jar")
    jenkinsTest("org.jenkins-ci.modules:instance-identity:2.1@jar")

    testCompile("junit:junit:4.12")
    testCompile("org.mockito:mockito-core:2.10.0")
    testCompile("com.nhaarman:mockito-kotlin-kt1.1:1.5.0")
    testCompile("org.assertj:assertj-core:3.8.0")
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

task<Delete>("cleanUp") {
    delete("target")
}

taskOf<Task>("clean").dependsOn("cleanUp")

task<Wrapper>("wrapper") {
    gradleVersion = "4.2.1"
    distributionType = DistributionType.ALL
}

// Helper methods.
inline fun <reified T : Task> taskOf(taskName: String) = project.tasks.findByName(taskName) as T


