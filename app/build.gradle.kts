plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pokewidgets.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pokewidgets.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sprites are pinned to an exact commit of PokeAPI/sprites so cached files
        // never go stale and jsDelivr can cache them permanently.
        buildConfigField(
            "String",
            "SPRITES_SHA",
            "\"c10459b9b0129eaca5c5d9b1cac65336debb1d08\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    /**
     * Every pin below exists because this module is stuck on AGP 7.4.1, and each one is a
     * splint rather than a fix. Deleting the whole block is the goal; the way there is to
     * upgrade Android Studio and AGP, not to relax them one at a time.
     *
     * **Never force a version below what a library on the classpath asks for.** A `force`
     * outranks a dependency's own `requires`, silently, and the result compiles cleanly
     * and then dies at runtime the first time the missing member is touched. This has now
     * happened twice:
     *
     *  - `androidx.core:core` was forced to 1.12.0 while Compose Foundation 1.7.6
     *    *requires* 1.13.1. Foundation's text field calls
     *    `EditorInfoCompat.setStylusHandwritingEnabled`, which does not exist before
     *    1.13.0, so focusing the search field threw `NoSuchMethodError`. There is no
     *    force on core any more: Gradle resolves 1.13.1 on its own because Foundation
     *    asks for it, and with nothing pinning it, it cannot drift below that again.
     *  - `lifecycle` had the mirror-image problem: we asked for it directly while Compose
     *    Material3 pulled `lifecycle-viewmodel-compose` transitively, and "highest wins"
     *    lifted only that one. The newer module reads `ViewModelProvider.Companion`, a
     *    field that only exists from 2.8.0, so a split classpath died with
     *    `NoSuchFieldError` on the first `viewModel()` call. Aligning the group makes
     *    that unrepresentable.
     *
     * The remaining pins are load-bearing for the *build*, not the runtime: AGP 7.4.1's
     * D8 throws `NullPointerException` while dexing `vectordrawable` 1.2.0,
     * `vectordrawable-animated` 1.2.0 and `profileinstaller` 1.4.1. Each is held at the
     * version its own consumer declares — Compose UI 1.7.6 requires exactly
     * `profileinstaller:1.3.1`, AppCompat 1.6.1 shipped against `vectordrawable:1.1.0` —
     * so none of them is pinned below a stated requirement.
     */
    configurations.all {
        resolutionStrategy {
            force("androidx.appcompat:appcompat:1.6.1")
            force("androidx.appcompat:appcompat-resources:1.6.1")
            force("androidx.activity:activity:1.8.2")
            force("androidx.activity:activity-compose:1.8.2")
            force("androidx.profileinstaller:profileinstaller:1.3.1")
            force("androidx.vectordrawable:vectordrawable:1.1.0")
            force("androidx.vectordrawable:vectordrawable-animated:1.1.0")
            force("androidx.lifecycle:lifecycle-livedata-core:2.7.0")
            force("androidx.lifecycle:lifecycle-livedata:2.7.0")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.glide.gifdecoder)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

val javaToolchains = extensions.getByType<JavaToolchainService>()
tasks.withType<JavaCompile>().configureEach {
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    })
}
