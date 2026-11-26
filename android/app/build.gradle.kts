plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}
val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")?.let { file(it) }
val hasReleaseSigning = keystoreFile?.exists() == true &&
  !System.getenv("ANDROID_KEYSTORE_PASSWORD").isNullOrEmpty() &&
  !System.getenv("ANDROID_KEY_ALIAS").isNullOrEmpty() &&
  !System.getenv("ANDROID_KEY_PASSWORD").isNullOrEmpty()

android {
  namespace = "org.local.oretsuba"
  compileSdk = 34
  defaultConfig {
    applicationId = "org.local.oretsuba"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0.0"
    externalNativeBuild {
      cmake {
        cppFlags += "-O2"
      }
    }
  }
  // One APK per ABI, no universal APK. Release builds 3.
  // Debug workflow runs assembleDebug -Pandroid.injected.build.abi=arm64-v8a for arm64 only.
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86_64")
      isUniversalApk = false
    }
  }
  // Release signing only when keystore env vars are set (CI release
  // workflow). Local assembleRelease without secrets still builds unsigned.
  signingConfigs {
    create("release") {
      if (hasReleaseSigning) {
        storeFile = keystoreFile
        storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("ANDROID_KEY_ALIAS")
        keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
      }
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isMinifyEnabled = false
      // Debug arm64-only is done by the workflow running
      // assembleDebug -Pandroid.injected.build.abi=arm64-v8a,
      // not by abiFilters (abiFilters + splits conflict).
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }
  packaging {
    jniLibs {
      useLegacyPackaging = false
    }
  }
}
dependencies {
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.core:core-ktx:1.13.1")
  implementation("androidx.documentfile:documentfile:1.0.1")
}
