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
     * Pins every `androidx.lifecycle` artifact to one version.
     *
     * Without this the module versions drift apart: we ask for lifecycle directly, but
     * Compose Material3 also depends on `lifecycle-viewmodel-compose`, and Gradle's
     * "highest wins" rule resolves that one transitively while leaving the others where
     * we declared them. A newer `lifecycle-viewmodel-compose` calls
     * `ViewModelProvider.Companion` — a field that only exists from 2.8.0, when the class
     * was rewritten in Kotlin — so a split classpath compiles cleanly and then dies with
     * `NoSuchFieldError` on the first composition that calls `viewModel()`. Aligning the
     * whole group makes that failure mode unrepresentable rather than merely unlikely.
     */
    configurations.all {
        resolutionStrategy {
            force("androidx.appcompat:appcompat:1.6.1")
            force("androidx.appcompat:appcompat-resources:1.6.1")
            force("androidx.activity:activity:1.8.2")
            force("androidx.activity:activity-compose:1.8.2")
            force("androidx.core:core-ktx:1.12.0")
            force("androidx.core:core:1.12.0")
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
