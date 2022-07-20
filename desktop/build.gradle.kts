plugins {
    id("kotlin")
    id("org.jetbrains.compose") version "1.1.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

val osArch = if (project.hasProperty("osarch")) project.property("osarch") as? String else null

dependencies {
    val decomposeVersion = "0.7.0"

    implementation(project(":common-jvm"))
    implementation(project(":sqldelight"))
    implementation("com.squareup.sqldelight:sqlite-driver:1.5.3")
    if (osArch != null) {
        implementation("org.jetbrains.compose.desktop:desktop-jvm-$osArch:${org.jetbrains.compose.ComposeBuildConfig.composeVersion}")
    } else {
        implementation(compose.desktop.currentOs)
    }
    implementation(compose.materialIconsExtended)
    implementation("com.github.MrStahlfelge.mosaik:common-compose:1bb776e3ee")

    implementation("com.arkivanov.decompose:decompose:$decomposeVersion")
    implementation("com.arkivanov.decompose:extensions-compose-jetbrains:$decomposeVersion")
    implementation("net.harawata:appdirs:1.2.1") // https://github.com/harawata/appdirs

    // https://levelup.gitconnected.com/qr-code-scanner-in-kotlin-e15dd9bfbb1f
    arrayOf("core","kotlin","WebcamCapture").forEach()
    { implementation("org.boofcv:boofcv-$it:0.40.1") {
        exclude("org.boofcv", "boofcv-swing")
    } }
}

val mainClassName = "org.ergoplatform.MainKt"
compose.desktop {
    application {
        mainClass = mainClassName
        nativeDistributions {
            modules("java.sql")
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "ergowalletapp"
        }
    }
}

tasks {
    processResources {
        doFirst {
            copy {
                from("../ios/resources/i18n")
                into("src/main/resources/i18n")
                include("*.properties")
            }
        }
    }
}

project.version = "1.10.2213"

val currentArch by lazy { System.getProperty("os.arch") }

val currentOS: String by lazy {
    val os = System.getProperty("os.name")
    when {
        os.equals("Mac OS X", ignoreCase = true) -> "macos"
        os.startsWith("Win", ignoreCase = true) -> "windows"
        os.startsWith("Linux", ignoreCase = true) -> "linux"
        else -> error("Unknown OS name: $os")
    }
}

val fileNameOsArch = osArch ?: "$currentOS-$currentArch"

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveFileName.set("ergo-wallet-app_${project.version}_$fileNameOsArch-full.jar")

        isZip64 = true
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to mainClassName))
        }
    }
}

val obfuscate by tasks.registering(proguard.gradle.ProGuardTask::class)

obfuscate.configure {
    injars(tasks.shadowJar)
    outjars(base.libsDirectory.file("ergo-wallet-app-${project.version}_$fileNameOsArch.jar"))

    libraryjars("${compose.desktop.application.javaHome ?: System.getProperty("java.home")}/jmods")

    configuration(listOf("proguard-rules.pro", "../android/proguard-rules.pro"))
}

tasks {
    build {
        dependsOn(obfuscate)
    }
}
