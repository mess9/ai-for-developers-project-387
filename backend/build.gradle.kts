plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.21.0"
    id("nu.studer.jooq") version "10.1"
}

group = "io.hexlet"
version = "0.0.1-SNAPSHOT"

// nu.studer.jooq 10.1 ожидает jOOQ 3.20.x; Spring Boot 4.0.6 BOM фиксирует 3.19.32.
// Форсируем 3.20.3 для всех конфигураций.
// Groovy 5.x имеет NPE в ClosureMetaClass на JDK 21+ при использовании с RestAssured — форсируем 4.0.22.
dependencyManagement {
    dependencies {
        listOf("jooq", "jooq-meta", "jooq-codegen", "jooq-meta-extensions").forEach { m ->
            dependency("org.jooq:$m:3.20.3")
        }
        listOf("groovy", "groovy-xml", "groovy-json").forEach { m ->
            dependency("org.apache.groovy:$m:4.0.22")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    // jOOQ DDL codegen — версия синхронизирована с nu.studer.jooq 10.1
    jooqGenerator("org.jooq:jooq-meta-extensions:3.20.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.3")
    testImplementation("org.testcontainers:postgresql:1.21.3")
    testImplementation("org.http4k:http4k-core:5.20.0.0")
    testImplementation("org.http4k:http4k-client-apache:5.20.0.0")
    testImplementation("org.http4k:http4k-format-jackson:5.20.0.0")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ──────────────────────────────────────────────
// OpenAPI codegen  →  build/generated/openapi
// ──────────────────────────────────────────────
val openApiOut = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("${rootDir}/../contract/openapi.yaml")
    outputDir.set(openApiOut.get().asFile.absolutePath)
    apiPackage.set("io.hexlet.booking.api")
    modelPackage.set("io.hexlet.booking.model")
    configOptions.set(
        mapOf(
            "interfaceOnly"         to "true",
            "useSpringBoot3"        to "true",   // Spring Boot 4 совместим с этим генератором
            "useBeanValidation"     to "true",
            "documentationProvider" to "none",
            "serializationLibrary"  to "jackson",
            "enumPropertyNaming"    to "UPPERCASE",
            "useOptional"           to "false",
            "skipDefaultInterface"  to "true",
            "dateLibrary"           to "java8",
        )
    )
    globalProperties.set(mapOf("modelDocs" to "false", "apiDocs" to "false"))
    // format: uri → String, иначе Hibernate Validator 8 не может применить @Size к java.net.URI
    typeMappings.set(mapOf("URI" to "kotlin.String"))
}

sourceSets.main {
    kotlin.srcDir(openApiOut.map { it.dir("src/main/kotlin") })
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

// ──────────────────────────────────────────────
// jOOQ codegen from DDL  →  build/generated/jooq
// ──────────────────────────────────────────────
jooq {
    version.set("3.20.3")
    configurations {
        create("main") {
            jooqConfiguration.apply {
                generator.apply {
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties.addAll(
                            listOf(
                                org.jooq.meta.jaxb.Property()
                                    .withKey("scripts")
                                    .withValue("${projectDir}/src/main/resources/db/migration"),
                                org.jooq.meta.jaxb.Property()
                                    .withKey("sort")
                                    .withValue("semantic"),
                                org.jooq.meta.jaxb.Property()
                                    .withKey("defaultNameCase")
                                    .withValue("lower_case"),
                            )
                        )
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "io.hexlet.booking.db"
                        directory = layout.buildDirectory.dir("generated/jooq").get().asFile.absolutePath
                    }
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// Синхронизируем Java компилятор с Kotlin target
tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Groovy (используется RestAssured) требует reflection access на JDK 24+
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
}
