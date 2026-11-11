plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "com.example.tradedraw.adapters"
    compileSdk = 34
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(project(":core"))
    implementation(project(":observability"))
    implementation(project(":market"))
    implementation(project(":ui"))
    implementation(project(":execution"))
    testImplementation("org.json:json:20240303")
    testImplementation(project(":risk"))
    testImplementation(libs.junit)
}
