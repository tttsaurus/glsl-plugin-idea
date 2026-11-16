import org.jetbrains.changelog.Changelog
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.grammarKit)
    alias(libs.plugins.changelog)
    alias(libs.plugins.kover)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Use JDK 25 for development while keeping the plugin runnable on 2024.1's JBR 17
kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = providers.fileContents(layout.projectDirectory.file("plugin-info/description.html")).asText

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            create("IC", "2025.2.6.2")
            create("IC", "2025.1.7.1")
            create("IC", "2024.3.7.1")
            create("IC", "2024.2.6")
            create("IC", "2024.1.7")
        }
    }
}

changelog {
    version = providers.gradleProperty("pluginVersion")
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

val generatedRoot = layout.projectDirectory.dir("src/main/gen")

val generateParser = tasks.named<GenerateParserTask>("generateParser") {
    sourceFile = layout.projectDirectory.file("grammar/GlslGrammar.bnf")
    targetRootOutputDir = generatedRoot.dir("parser")
    pathToParser = "glsl/_GlslParser.java"
    pathToPsiRoot = "glsl/psi"
    purgeOldFiles = true
}

val generateLexer = tasks.named<GenerateLexerTask>("generateLexer") {
    sourceFile = layout.projectDirectory.file("grammar/GlslLexer.flex")
    targetOutputDir = generatedRoot.dir("lexer")
    purgeOldFiles = true
}

val generateHighlightLexer = tasks.register<GenerateLexerTask>("generateHighlightLexer") {
    sourceFile = layout.projectDirectory.file("grammar/GlslHighlightLexer.flex")
    targetOutputDir = generatedRoot.dir("highlightLexer")
    purgeOldFiles = true
}

val generateGrammar = tasks.register("generateGrammar") {
    group = "grammar"
    description = "Generate the GLSL parser, PSI and both lexers."
    dependsOn(generateParser, generateLexer, generateHighlightLexer)
}

val cleanGeneratedGrammar = tasks.register<Delete>("cleanGeneratedGrammar") {
    group = "grammar"
    description = "Delete generated GLSL sources."
    delete(generatedRoot)
}

listOf(generateParser, generateLexer, generateHighlightLexer).forEach {
    it.configure { mustRunAfter(cleanGeneratedGrammar) }
}

tasks.register("generateGrammarClean") {
    group = "grammar"
    description = "Clean and regenerate the GLSL parser, PSI and both lexers."
    dependsOn(cleanGeneratedGrammar, generateGrammar)
}

sourceSets {
    main {
        java.srcDirs(generatedRoot.dir("parser"), generatedRoot.dir("lexer"), generatedRoot.dir("highlightLexer"))
    }
}

tasks {
    compileJava {
        dependsOn(generateGrammar)
    }
    compileKotlin {
        dependsOn(generateGrammar)
    }
    runIde {
        maxHeapSize = "6g"
    }
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }
}
