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
dependencyManagement {
    dependencies {
        listOf("jooq", "jooq-meta", "jooq-codegen", "jooq-meta-extensions").forEach { m ->
            dependency("org.jooq:$m:3.20.3")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
    // Testcontainers 2.x переименовал артефакты: добавлен префикс testcontainers-.
    // Версия управляется Spring Boot 4 BOM (импортирует testcontainers-bom:2.0.5).
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.http4k:http4k-core:5.20.0.0")
    testImplementation("org.http4k:http4k-client-apache:5.20.0.0")
    testImplementation("org.http4k:http4k-format-jackson:5.20.0.0")
    // Jackson 2.x JSR-310 нужен тестовому ObjectMapper (приложение использует Jackson 3.x)
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ──────────────────────────────────────────────
// TypeSpec → OpenAPI  (источник правды: contract/*.tsp)
// ──────────────────────────────────────────────
val contractDir = file("${rootDir}/../contract")
val isWindows = System.getProperty("os.name").lowercase().contains("win")
val npmCmd = if (isWindows) "npm.cmd" else "npm"

// Установка зависимостей TypeSpec (idempotent: пропускается, пока lockfile не менялся).
val tspInstall = tasks.register<Exec>("tspInstall") {
    workingDir = contractDir
    commandLine(npmCmd, "ci")
    inputs.file(contractDir.resolve("package.json"))
    inputs.file(contractDir.resolve("package-lock.json"))
    outputs.dir(contractDir.resolve("node_modules"))
}

// Компиляция TypeSpec → contract/tsp-output/openapi.yaml.
val tspCompile = tasks.register<Exec>("tspCompile") {
    dependsOn(tspInstall)
    workingDir = contractDir
    commandLine(npmCmd, "run", "build")
    inputs.files(fileTree(contractDir) {
        include("*.tsp")
        include("tspconfig.yaml")
    })
    outputs.dir(contractDir.resolve("tsp-output"))
}

// ──────────────────────────────────────────────
// OpenAPI codegen  →  build/generated/openapi
// ──────────────────────────────────────────────
val openApiOut = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("${contractDir}/tsp-output/openapi.yaml")
    outputDir.set(openApiOut.map { it.asFile.absolutePath })
    apiPackage.set("io.hexlet.booking.api")
    modelPackage.set("io.hexlet.booking.model")
    configOptions.set(
        mapOf(
            "interfaceOnly"         to "true",
            "useSpringBoot3"        to "true",
            "useBeanValidation"     to "true",
            "documentationProvider" to "none",
            "serializationLibrary"  to "jackson",
            "enumPropertyNaming"    to "UPPERCASE",
            "useOptional"           to "false",
            "skipDefaultInterface"  to "true",
            "dateLibrary"           to "java8",
            "apiNamePrefix"         to "",
            "apiNameSuffix"         to "Api",
            "generateConstructorInjection" to "true"
        )
    )
    additionalProperties.set(
        mapOf(
            "useRestController" to "false"
        )
    )
    globalProperties.set(mapOf("modelDocs" to "false", "apiDocs" to "false"))
    // format: uri → String, иначе Hibernate Validator 8 не может применить @Size к java.net.URI
    typeMappings.set(mapOf("URI" to "kotlin.String"))
}

// OpenAPI генерится из свежескомпилированного контракта.
tasks.named("openApiGenerate") {
    dependsOn(tspCompile)
}

sourceSets.main {
    kotlin.srcDir(openApiOut.map { it.dir("src/main/kotlin") })
}

// Страховочный шаг: убирает @RestController из сгенерированных интерфейсов, если генератор
// вдруг добавит его (сейчас не добавляет, т.к. useRestController=false).
// Компиляция зависит от этого шага, а тот — от openApiGenerate, поэтому явный
// dependsOn("openApiGenerate") в compileKotlin не нужен.
tasks.register("fixOpenApiGenerated") {
    dependsOn("openApiGenerate")
    doLast {
        val apiDir = layout.buildDirectory
            .dir("generated/openapi/src/main/kotlin/io/hexlet/booking/api")
            .get().asFile
        val apiFiles = apiDir.listFiles { f -> f.name.endsWith("Api.kt") } ?: return@doLast
        apiFiles.forEach { file ->
            val content = file.readText()
            if (content.contains("@RestController")) {
                file.writeText(
                    content.replace("@RestController\n", "").replace("@RestController\r\n", "")
                )
            }
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("fixOpenApiGenerated")
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
    // Открываем модули для рефлексии, которую используют http4k / Spring Test на JDK 17+
    jvmArgs(
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
        "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
    )
}
