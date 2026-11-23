import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

val versionProps = Properties().apply {
  val versionFile = rootProject.file("version.properties") // PLAT-R07
  if (versionFile.exists()) {
    versionFile.inputStream().use { load(it) }
  }
}

val appVersionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull()
  ?: versionProps.getProperty("versionCode", "118").toInt()

val appVersionName = (project.findProperty("versionName") as? String)
  ?: versionProps.getProperty("versionName", "1.1.29")

android {
  namespace = "com.example"
  compileSdk = 34

  defaultConfig {
    applicationId = "com.aistudio.networktoolbox.kpqrsy"
    minSdk = 24
    targetSdk = 36
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseKeystoreFile = file(System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks")
      signingConfig = if (releaseKeystoreFile.exists()) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debugConfig")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
      isMinifyEnabled = false
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
    buildConfig = true
    dataBinding = false
    viewBinding = false
  }
  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.10"
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  
  packaging {
    resources {
      excludes += setOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/license.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/notice.txt",
        "META-INF/ASL2.0",
        "META-INF/*.kotlin_module"
      )
    }
  }
}

// Force Kotlin stdlib version to avoid compatibility issues
configurations.all {
  resolutionStrategy {
    force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
    force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  // Force Kotlin stdlib version to match Kotlin 1.9.22
  implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.9.22"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
  
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.zxing.core)
  implementation(libs.mlkit.barcode)
  implementation(libs.sqlcipher)
  implementation(libs.jsch)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  
  // Google Drive & Authentication
  implementation("com.google.android.gms:play-services-auth:21.0.0")
  implementation("com.google.api-client:google-api-client-android:1.33.0") {
      exclude(group = "org.apache.httpcomponents")
  }
  implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
      exclude(group = "org.apache.httpcomponents")
  }
  implementation("com.google.http-client:google-http-client-gson:1.42.3")
  
  // Chromecast Framework (Google Cast SDK)
  implementation(libs.play.services.cast.framework)
  implementation(libs.androidx.mediarouter)

  // Biometric authentication (Password Vault)
  implementation("androidx.biometric:biometric:1.1.0")
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  implementation("androidx.appcompat:appcompat:1.6.1")
}
