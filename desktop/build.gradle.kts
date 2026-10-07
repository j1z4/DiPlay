import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

// DiPlay's protocol core is pure JVM Kotlin. Mirror the portable subset of :shared into the
// build directory so the Windows receiver and the Android app share one implementation, while
// desktop/src keeps its own stand-ins (android.util.Log, TcpLiveness) without name clashes.
val syncSharedSources by tasks.registering(Sync::class) {
    from(file("../shared/src/main/java")) {
        include(
            "com/shilapi/xcertplay/iap2/**",
            "com/shilapi/xcertplay/mfi/**",
            "com/shilapi/xcertplay/transport/Iap2*.kt",
            "com/shilapi/xcertplay/transport/VehicleSpeedNmea.kt",
            "com/shilapi/xcertplay/transport/BlockingDuplexByteStream.kt",
            "com/shilapi/xcertplay/transport/I2cTransport.kt",
            "com/shilapi/xcertplay/transport/IphoneUsbException.kt",
            "com/shilapi/xcertplay/transport/RfcommDuplexStream.kt",
            "com/shilapi/xcertplay/airplay/**",
            "com/shilapi/xcertplay/network/AirPlayPortSelector.kt",
            "com/shilapi/xcertplay/network/CarPlayBonjourProtocol.kt",
            "com/shilapi/xcertplay/network/WirelessHotspotManager.kt",
            "com/shilapi/xcertplay/network/WirelessStartupPolicy.kt",
            "com/shilapi/xcertplay/orchestration/FirstTcpWatchdog.kt",
            "com/shilapi/xcertplay/media/TouchLatencyProbe.kt",
            "com/shilapi/xcertplay/media/MediaCodecSupport.kt",
        )
        exclude(
            // USB-only; depends on the Android USB host stack.
            "com/shilapi/xcertplay/transport/Iap2UsbMuxHost.kt",
            // Android key events; unused by the AirPlay session and media engine.
            "com/shilapi/xcertplay/airplay/CarPlayMediaButton.kt",
        )
    }
    into(layout.buildDirectory.dir("shared-src"))
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    sourceSets.main {
        kotlin.srcDir(syncSharedSources)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
    implementation("org.jmdns:jmdns:3.6.3")
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")
    // Modern look for the dashboard.
    implementation("com.formdev:flatlaf:3.6")
    // FFmpeg decoding through JavaCPP; ship only the Windows x64 natives.
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11")
    implementation("org.bytedeco:javacpp:1.5.11")
    runtimeOnly("org.bytedeco:ffmpeg:7.1-1.5.11:windows-x86_64")
    runtimeOnly("org.bytedeco:javacpp:1.5.11:windows-x86_64")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.shilapi.xcertplay.desktop.OpenPlayKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

// Standalone Windows app folder (OpenPlay.exe plus a private Java runtime) via jpackage.
// The accessory identity is not bundled; settings.properties points at it.
val packageInput = layout.buildDirectory.dir("package-input")

val stagePackageInput by tasks.registering(Sync::class) {
    from(tasks.jar)
    from(configurations.runtimeClasspath)
    into(packageInput)
}

val packageOpenPlay by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds build/package/OpenPlay with OpenPlay.exe and a bundled runtime."
    dependsOn(stagePackageInput)
    val output = layout.buildDirectory.dir("package").get().asFile
    doFirst { output.resolve("OpenPlay").deleteRecursively() }
    executable = File(System.getProperty("java.home"), "bin/jpackage.exe").absolutePath
    args(
        "--type", "app-image",
        "--name", "OpenPlay",
        "--app-version", "0.1.0",
        "--vendor", "OpenPlay",
        "--input", packageInput.get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", application.mainClass.get(),
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--dest", output.absolutePath,
    )
}
