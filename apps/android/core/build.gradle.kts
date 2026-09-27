// Noyau partagé par l'app téléphone et l'app Wear OS : portages de rega,
// luach et du pont UT1, synchronisation avec /api/now, calibration stockée.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "technology.zman.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // org.json d'Android n'est qu'un stub sur la JVM
}
