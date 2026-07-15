import net.pwall.json.kotlin.codegen.gradle.JSONSchemaCodegen
import net.pwall.json.kotlin.codegen.gradle.JSONSchemaCodegenPlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("net.pwall.json:json-kotlin-gradle:0.121")
    }
}

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.apollographql.apollo") version "4.4.3"
    id("org.openapi.generator") version "7.23.0"
}

apply<JSONSchemaCodegenPlugin>()

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

val openApiGeneratedDir = layout.buildDirectory.dir("generated/openapi")
val jsonSchemaGeneratedDir = layout.projectDirectory.dir("build/generated-sources/kotlin")

apollo {
    service("books") {
        packageName.set("com.example.graphql")
        // Schema lives under schemas/graphql, separate from generated client
        // code and from the operation (.graphql) files under src/main/graphql.
        schemaFile.set(file("schemas/graphql/schema.graphqls"))
    }
}

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    // Schema lives under schemas/openapi, separate from the generated client.
    inputSpec.set(layout.projectDirectory.file("schemas/openapi/tasks-api.yaml").asFile.path)
    outputDir.set(openApiGeneratedDir.get().asFile.path)
    packageName.set("com.example.tasks")
    apiPackage.set("com.example.tasks.apis")
    modelPackage.set("com.example.tasks.models")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "moshi",
            "dateLibrary" to "java8",
        )
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false",
            "apiDocs" to "false",
            "modelDocs" to "false",
        )
    )
}

configure<JSONSchemaCodegen> {
    packageName.set("com.example.users")
    generatorComment.set("Generated from schemas/jsonSchema/user.json")
    inputs {
        inputFile(file("schemas/jsonSchema/user.json"))
    }
}

sourceSets {
    main {
        kotlin.srcDir(openApiGeneratedDir.map { it.dir("src/main/kotlin") })
        kotlin.srcDir(jsonSchemaGeneratedDir)
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn("openApiGenerate")
    dependsOn("generate")
}

val ktorVersion = "3.1.3"

dependencies {
    implementation("com.apollographql.apollo:apollo-runtime:4.4.3")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.moshi:moshi-adapters:1.15.1")
    implementation("io.jstuff:json-validation:4.0")

    testImplementation(kotlin("test"))
    testImplementation(kotlin("reflect"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    testImplementation("io.ktor:ktor-client-core:$ktorVersion")
    testImplementation("io.ktor:ktor-client-cio:$ktorVersion")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}
