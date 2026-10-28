import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Единственный источник правды для scope модуля — файл `app/module-scope.txt`.
 * Из него генерируются массив ресурсов `module_scope` (легаси-формат Xposed)
 * и `META-INF/xposed/scope.list` вместе с остальными метаданными нового формата LSPosed.
 */
abstract class GenerateXposedMetadata : DefaultTask() {

    @get:Input
    abstract val scopePackages: ListProperty<String>

    @get:Input
    abstract val entryClass: Property<String>

    @get:Input
    abstract val moduleId: Property<String>

    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    abstract val moduleVersion: Property<String>

    @get:Input
    abstract val moduleVersionCode: Property<Int>

    @get:Input
    abstract val moduleAuthor: Property<String>

    @get:Input
    abstract val moduleDescription: Property<String>

    @get:Input
    abstract val minApi: Property<Int>

    @get:OutputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputDirectory
    abstract val javaResourcesDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val packages = scopePackages.get()

        val values = resDir.get().asFile.resolve("values").apply { mkdirs() }
        values.resolve("module_scope.xml").writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("<!-- Сгенерировано из app/module-scope.txt, не редактировать -->")
                appendLine("<resources>")
                appendLine("""    <string-array name="module_scope">""")
                packages.forEach { appendLine("        <item>$it</item>") }
                appendLine("    </string-array>")
                appendLine("</resources>")
            }
        )

        val xposedDir = javaResourcesDir.get().asFile.resolve("META-INF/xposed").apply { mkdirs() }
        xposedDir.resolve("java_init.list").writeText(entryClass.get() + "\n")
        xposedDir.resolve("scope.list").writeText(
            if (packages.isEmpty()) "" else packages.joinToString(separator = "\n", postfix = "\n")
        )
        xposedDir.resolve("module.prop").writeText(
            buildString {
                appendLine("id=${moduleId.get()}")
                appendLine("name=${moduleName.get()}")
                appendLine("version=${moduleVersion.get()}")
                appendLine("versionCode=${moduleVersionCode.get()}")
                appendLine("author=${moduleAuthor.get()}")
                appendLine("description=${moduleDescription.get()}")
                appendLine("minApiVersion=${minApi.get()}")
                appendLine("targetApiVersion=102")
                appendLine("staticScope=false")
            }
        )
    }
}

val moduleScopePackages: Provider<List<String>> =
    providers.fileContents(layout.projectDirectory.file("module-scope.txt")).asText
        .map { text ->
            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .toList()
        }
        .orElse(emptyList())

/**
 * Версия сборки. По умолчанию `1` — этого достаточно для сборок из main;
 * релизная версия передаётся ручным запуском: `-PhonorVersion=1.2.3`.
 */
val appVersionName: String = providers.gradleProperty("honorVersion").orNull
    ?.trim()
    ?.removePrefix("v")
    ?.removePrefix("V")
    ?.takeIf { it.isNotEmpty() }
    ?: "1"

/** `1.2.3` → 10203, `1` → 1: код версии всегда растёт вместе с именем. */
fun versionCodeOf(name: String): Int {
    val clean = name.removePrefix("v").removePrefix("V")
    val parts = clean.split('.').map { it.toIntOrNull() ?: 0 }
    val major = parts.getOrElse(0) { 0 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    return (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

val appVersionCode = versionCodeOf(appVersionName)
val hookEntryClass = "com.fantom3031.honortool.core.HookEntry"

val generateXposedMetadata = tasks.register<GenerateXposedMetadata>("generateXposedMetadata") {
    group = "xposed"
    description = "Генерирует метаданные Xposed/LSPosed из app/module-scope.txt"

    scopePackages.set(moduleScopePackages)
    entryClass.set(hookEntryClass)
    moduleId.set("honor-tool")
    moduleName.set("Honor Tool")
    moduleVersion.set(appVersionName)
    moduleVersionCode.set(appVersionCode)
    moduleAuthor.set("fantom3031")
    moduleDescription.set("A modular set of hooks for Honor MagicOS firmware (LibXposed)")
    minApi.set(101)

    resDir.set(layout.buildDirectory.dir("generated/xposed/res"))
    javaResourcesDir.set(layout.buildDirectory.dir("generated/xposed/resources"))
}

android {
    namespace = "com.fantom3031.honortool"
    compileSdk = 35

    // В SDK установлена именно эта версия NDK (AGP по умолчанию ищет другую).
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.fantom3031.honortool"
        minSdk = 27
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            // PureDOOM: собираем только под arm64 (устройства Honor).
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/main/jni/Android.mk")
        }
    }

    signingConfigs {
        val storeFilePath = System.getenv("HONOR_KEYSTORE_FILE")
        if (!storeFilePath.isNullOrBlank() && file(storeFilePath).exists()) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("HONOR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HONOR_KEY_ALIAS")
                keyPassword = System.getenv("HONOR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            // Ресурсов мало, а `@array/module_scope` и строки нужны хукам,
            // поэтому шринкинг ресурсов не включаем — выгоды нет, риск есть.
            isShrinkResources = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].res.srcDir(generateXposedMetadata.flatMap { it.resDir })
    sourceSets["main"].resources.srcDir(generateXposedMetadata.flatMap { it.javaResourcesDir })

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }

    packaging {
        // Извлекаем libdoomhook.so на установке: иначе System.load из
        // nativeLibraryDir в чужом процессе (com.android.settings) не сработает.
        jniLibs {
            useLegacyPackaging = true
        }
        resources.excludes += setOf(
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
            "META-INF/*.version",
            "kotlin/**"
        )
    }
}

base.archivesName.set("honor-tool-$appVersionName")

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.named("preBuild") {
    dependsOn(generateXposedMetadata)
}

// LibXposed AARs объявляют minCompileSdk=37 (будущие версии Android),
// но используют стандартный байткод Java 17, совместимый с Android 8.0+.
// Отключаем проверку метаданных AAR, чтобы сборка не требовала неподдерживаемый SDK.
tasks.configureEach {
    if (name.startsWith("check") && name.endsWith("AarMetadata")) {
        enabled = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.exifinterface)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
