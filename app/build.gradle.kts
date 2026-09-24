plugins {
    id("com.android.application") version "8.6.0"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.deadpacketsociety.fieldcontroller"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.deadpacketsociety.fieldcontroller"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
}


