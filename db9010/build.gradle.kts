import org.jetbrains.dokka.gradle.DokkaTask
import java.io.File
import kotlin.text.Regex

plugins {
    java
    kotlin("jvm") // version set in parent
    id("org.jetbrains.dokka") version "0.10.1"
    `maven-publish`
}

buildscript {
    // dokka requires a repository from which to download dokka-fatjar on demand
    configure(listOf(repositories, project.repositories)) {
        jcenter()
        maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
    }
}

group = "com.jovial"
version = "0.1.0"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testCompile("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

object PostProcessHack {
    val imageRegex = Regex("""\${'$'}IMAGE[ \t]*\(([^)]*)\)""")
    // Yow.  That matches "$IMAGE (foo.svg)", with MatchResult's
    // groupValues[1] containing "foo.svg".  You're welcome.

    fun process(dir: File) {
        for (f : File in dir.listFiles()) {
            if (f.isDirectory) {
                process(f)
            } else if (f.name.endsWith(".html")) {
                var modified = false
                val text = f.readText()
                val result = imageRegex.replace(text) { mr ->
                    modified = true
                    """<img src=${mr.groupValues[1]}>"""
                }
                if (modified) f.writeText(result)
            }
        }
    }
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    val docsOutputDir = "../docs"

    val copyDocAssets by registering(Copy::class) {
        from("src/docs")
        include("**/*.svg")
        include("**/*.jpg")
        include("**/*.png")
        include("**/*.html")
        into("$docsOutputDir/db9010")
    }

    val dokka by getting(DokkaTask::class) {
        dependsOn(copyDocAssets)
        outputFormat = "html"
        outputDirectory = docsOutputDir
        this.configuration
        configuration {
            includes = listOf(
                "src/docs/module.md",
                "src/docs/com.jovial.db9010/package.md"
            )
        }
        doLast {
            PostProcessHack.process(File("$outputDirectory/db9010"))
        }
    }

    val dokkaJar by creating(Jar::class) {
        dependsOn(dokka)
        group = JavaBasePlugin.DOCUMENTATION_GROUP
        description = "Assembles Kotlin docs with Dokka"
        getArchiveClassifier().set("javadoc")
        from("$docsOutputDir/db9010")
    }

    val sourcesJar by creating(Jar::class) {
        description = "Assembles source JAR file"
        getArchiveClassifier().set("sources")
        from(sourceSets["main"].allSource)
    }

    publishing {
        repositories {
            maven {
            url = uri("file:///home/billf/github/zathras.github.io/maven")
            /*  For Github Packages:
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/zathras/db9010")
                credentials {
                    val home = System.getenv("HOME")
                    username = File("$home/.ssh/other/github.maven.user").readText().trim()
                    password = File("$home/.ssh/other/github.maven.token").readText().trim()
                }
            */
            }
        }
        publications {
            // create<MavenPublication>("default") {
            register<MavenPublication>("gpr") {
                from(project.components["java"])
                artifact(dokkaJar)
                artifact(sourcesJar)
            }
        }
    }
}
