plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.undercover.game"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.undercover.game"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "2.2"
    }
    // Signature : en CI, la clé vient des secrets GitHub (UC_KEYSTORE…) ; en local, clé de debug.
    // Il faut TOUJOURS la même clé, sinon les mises à jour ne s'installent pas par-dessus.
    signingConfigs {
        create("release") {
            val ks = System.getenv("UC_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("UC_STORE_PASSWORD")
                keyAlias = System.getenv("UC_KEY_ALIAS")
                keyPassword = System.getenv("UC_KEY_PASSWORD")
            } else {
                val dbg = signingConfigs.getByName("debug")
                storeFile = dbg.storeFile; storePassword = dbg.storePassword
                keyAlias = dbg.keyAlias; keyPassword = dbg.keyPassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.04.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
