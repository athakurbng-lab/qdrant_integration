plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.qdrant"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.example.qdrant"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":qdrant-android-sdk"))
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity:1.8.0")
    implementation("com.google.android.material:material:1.11.0")
}
