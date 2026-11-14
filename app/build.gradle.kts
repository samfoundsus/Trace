import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// Read version numbers from root version.properties (single source of truth).
// VERSION_CODE_OFFSET is added to buildNumber so versionCode strictly increases.
// If the CI workflow is ever recreated and run numbers reset, raise VERSION_CODE_OFFSET in version.properties.
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
  if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { load(it) }
  } else {
    throw GradleException("Missing version.properties at project root: ${versionPropsFile.absolutePath}")
  }
}

val versionMajor: Int = versionProps.getProperty("VERSION_MAJOR")?.trim()?.toIntOrNull()
  ?: throw GradleException("Invalid or missing VERSION_MAJOR in version.properties")
val versionMinor: Int = versionProps.getProperty("VERSION_MINOR")?.trim()?.toIntOrNull()
  ?: throw GradleException("Invalid or missing VERSION_MINOR in version.properties")
val versionPatch: Int = versionProps.getProperty("VERSION_PATCH")?.trim()?.toIntOrNull()
  ?: throw GradleException("Invalid or missing VERSION_PATCH in version.properties")
val versionCodeOffset: Int = versionProps.getProperty("VERSION_CODE_OFFSET")?.trim()?.toIntOrNull()
  ?: throw GradleException("Invalid or missing VERSION_CODE_OFFSET in version.properties")

val baseVersionName = "$versionMajor.$versionMinor.$versionPatch"

val ciBuildNumber: Int? =
  project.findProperty("buildNumber")?.toString()?.trim()?.toIntOrNull()
    ?: System.getenv("GITHUB_RUN_NUMBER")?.trim()?.toIntOrNull()

val resolvedBuildNumber: Int = ciBuildNumber ?: 1
val resolvedVersionCode: Int = resolvedBuildNumber + versionCodeOffset
val resolvedVersionName: String =
  if (ciBuildNumber != null) {
    "$baseVersionName (build $resolvedBuildNumber)"
  } else {
    baseVersionName
  }

val resolvedGitCommit: String =
  project.findProperty("gitCommit")?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getenv("GITHUB_SHA")?.trim()?.takeIf { it.isNotEmpty() }?.take(7)
    ?: "local"

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.trace.vxkpqm"
    minSdk = 29
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    buildConfigField("String", "BASE_VERSION_NAME", "\"$baseVersionName\"")
    buildConfigField("int", "BUILD_NUMBER", "$resolvedBuildNumber")
    buildConfigField("String", "GIT_COMMIT", "\"$resolvedGitCommit\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
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
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

composeCompiler {
  // Strong skipping mode is enabled by default in Kotlin 2.0+; explicitly configured here for clarity.
  @Suppress("DEPRECATION")
  enableStrongSkippingMode = true
  includeSourceInformation = true
}

// Fail release builds with a clear message if release signing credentials or keystore are missing.
gradle.taskGraph.whenReady {
  val isReleaseSigningRequested = allTasks.any {
    it.name.contains("validateSigningRelease", ignoreCase = true) ||
      it.name.contains("packageRelease", ignoreCase = true)
  }
  if (isReleaseSigningRequested) {
    val releaseConfig = android.signingConfigs.getByName("release")
    val ksFile = releaseConfig.storeFile
    val hasStorePass = !releaseConfig.storePassword.isNullOrBlank()
    val hasKeyPass = !releaseConfig.keyPassword.isNullOrBlank()
    if (ksFile == null || !ksFile.exists() || !hasStorePass || !hasKeyPass) {
      throw GradleException(
        "Release signing configuration is incomplete. Ensure KEYSTORE_PATH (pointing to an existing .jks file), " +
          "STORE_PASSWORD, and KEY_PASSWORD environment variables are set before running assembleRelease."
      )
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
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
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.google.ar.core)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
