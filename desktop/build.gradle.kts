import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    application
}

// DiPlay's protocol core is pure JVM Kotlin; compile it in place from :shared so the
// Windows receiver and the Android app share one implementation.
val sharedSources = file("../shared/src/main/java")

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    sourceSets.main {
        kotlin.srcDir(sharedSources)
        kotlin.include(
            "com/shilapi/xcertplay/iap2/**",
            "com/shilapi/xcertplay/mfi/**",
            "com/shilapi/xcertplay/transport/Iap2*.kt",
            "com/shilapi/xcertplay/transport/BlockingDuplexByteStream.kt",
            "com/shilapi/xcertplay/transport/I2cTransport.kt",
            "com/shilapi/xcertplay/transport/RfcommDuplexStream.kt",
            "com/shilapi/xcertplay/transport/IphoneUsbException.kt",
            "com/shilapi/xcertplay/desktop/**",
        )
        // USB-only; depends on the Android USB host stack.
        kotlin.exclude("com/shilapi/xcertplay/transport/Iap2UsbMuxHost.kt")
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
    testImplementation("junit:junit:4.13.2")
}

application {
    mainClass.set("com.shilapi.xcertplay.desktop.BluetoothProbeKt")
}
