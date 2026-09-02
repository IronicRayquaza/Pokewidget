import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Signing credentials, read from a file that is deliberately absent from the repo. On a public
 * repo a leaked signing key is unrecoverable: every future update would have to ship as a
 * fresh install, and every configured widget would be lost.
 *
 * Absent is a supported state — a fresh clone must still build and run the tests — so only
 * `assembleRelease` consumes this, and with no file the release APK comes out unsigned rather
 * than quietly signed with the machine-local debug key.
 *
 * Declared here rather than inside `android { }`, where `java` resolves to Gradle's own Java
 * extension instead of the package root.
 */
val keystoreProperties = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

android {
    namespace = "com.pokewidgets.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pokewidgets.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sprites are pinned to an exact commit of PokeAPI/sprites so cached files
        // never go stale and jsDelivr can cache them permanently.
        buildConfigField(
            "String",
            "SPRITES_SHA",
            "\"c10459b9b0129eaca5c5d9b1cac65336debb1d08\"",
        )
    }

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Null when there is no `keystore.properties`, which leaves the APK unsigned
            // rather than silently debug-signed. See `keystore.properties.example`.
            signingConfig = signingConfigs.findByName("release")

            // R8 is off, on purpose, for the first build that testers install.
            //
            // `WidgetConfigStore` persists enum *names* and reads them back through
            // `runCatching { enumValueOf<T>(it) }.getOrNull() ?: fallback`, so a rename by R8
            // does not crash — it silently resets every widget already on a home screen,
            // which is close to the worst thing to have to diagnose from a bug report.
            // Turning it on is its own task, with keep rules and a device test behind it.
            // Until then this build differs from the tested debug build in exactly one way:
            // it is signed with a real key.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            // Testers report against a build, not a commit. Showing the suffix in Settings →
            // Apps is the cheapest way to know which one they are actually running.
            versionNameSuffix = "-debug"
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
     * `vectordrawable-animated` 1.2.0, `profileinstaller` 1.4.1 and
     * `lifecycle-livedata-core` 2.8.7 —
     *
     *     D8: java.lang.NullPointerException: Cannot invoke "String.length()"
     *     because "<parameter1>" is null
     *
     * — so removing any of them fails `mergeExtDexDebug` rather than shipping something
     * broken, which is the failure mode to prefer. Each is held at the version its own
     * consumer declares: Compose UI 1.7.6 requires exactly `profileinstaller:1.3.1`,
     * AppCompat 1.6.1 shipped against `vectordrawable:1.1.0`.
     *
     * `livedata` is the exception that proves the rule above, so it is spelled out. It is
     * genuinely held *below* what the 2.8.7 lifecycle modules ask for, because D8 cannot
     * dex 2.8.7 at all — but all three livedata artifacts are pinned **together**, so the
     * family is internally consistent. Pinning only two of them, as this block did until
     * now, left `livedata-core-ktx` at 2.8.7 against a 2.7.0 `livedata-core`: a split
     * classpath of exactly the shape the second bullet describes, left behind by the fix
     * for it. Nothing in this app touches `LiveData` directly; it arrives only through
     * `SavedStateHandle`, which is why the mismatch had not yet cost anything.
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
            // The whole livedata family, together. See the note above.
            force("androidx.lifecycle:lifecycle-livedata-core:2.7.0")
            force("androidx.lifecycle:lifecycle-livedata-core-ktx:2.7.0")
            force("androidx.lifecycle:lifecycle-livedata:2.7.0")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // `androidx.core:core` arrives transitively — through core-ktx and through Compose
    // Foundation — so nothing above states its floor, and a `force` in the block above has
    // now dragged it under that floor twice, each time crashing the search field with
    // `NoSuchMethodError: EditorInfoCompat.setStylusHandwritingEnabled` the moment the field
    // took focus. A constraint is a floor rather than a ceiling, and `because` is printed by
    // `dependencyInsight` right beside whatever tries to lower it next.
    constraints {
        implementation("androidx.core:core") {
            version { require(libs.versions.coreKtx.get()) }
            because("Compose Foundation 1.7.6's text field calls EditorInfoCompat.setStylusHandwritingEnabled, which lands in core 1.13.0")
        }
    }

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
