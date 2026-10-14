plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Optional release signing, passed as Gradle properties so no keystore ever lands in git:
 *
 *     ./gradlew assembleRelease \
 *         -PreleaseKeystorePath=/secrets/wrc03.jks \
 *         -PreleaseStorePassword=... \
 *         -PreleaseKeyAlias=... \
 *         -PreleaseKeyPassword=...
 *
 * Without them the release build falls back to the local debug key. That still yields an
 * installable, properly signed APK — which is what CI attaches to a GitHub release — it
 * just is not a stable upload key for Play-style distribution.
 */
val releaseKeystorePath = providers.gradleProperty("releaseKeystorePath").orNull?.let { file(it) }
val releaseSigningReady = releaseKeystorePath?.exists() == true

android {
    namespace = "com.azahar4bd.walton.wrc03"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.azahar4bd.walton.wrc03"
        minSdk = 23
        targetSdk = 35
        // 0.x => the IR code table shipped with this build is NOT verified (see README).
        // Code scan mode lets a user confirm codes on their own TV; the table itself is
        // still community sourced.
        versionCode = 2
        versionName = "0.2.0-unverified"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = releaseKeystorePath
                storePassword = providers.gradleProperty("releaseStorePassword").orNull
                keyAlias = providers.gradleProperty("releaseKeyAlias").orNull
                keyPassword = providers.gradleProperty("releaseKeyPassword").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (releaseSigningReady) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
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
        viewBinding = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    testImplementation(libs.junit)
}
