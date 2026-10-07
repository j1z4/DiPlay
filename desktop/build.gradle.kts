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
    // FFmpeg decoding through JavaCPP; ship only the Windows x64 natives.
    implementation("org.bytedeco:ffmpeg:7.1-1.5.11")
    implementation("org.bytedeco:javacpp:1.5.11")
    runtimeOnly("org.bytedeco:ffmpeg:7.1-1.5.11:windows-x86_64")
    runtimeOnly("org.bytedeco:javacpp:1.5.11:windows-x86_64")
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.shilapi.xcertplay.desktop.OpenPlayKt")
}
