plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tyejaedon.coverscreenos"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tyejaedon.coverscreenos"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

val guardRemovedHomeScreen = tasks.register("guardRemovedHomeScreen") {
    group = "verification"
    description = "Fails if a removed homescreen UI directory is recreated."
    val removedDirectories = listOf("main", "test", "androidTest").map { sourceSet ->
        layout.projectDirectory.dir(
            "src/$sourceSet/java/com/tyejaedon/coverscreenos/ui/homescreen"
        ).asFile
    }
    doLast {
        removedDirectories.forEach { removedDirectory ->
            check(!removedDirectory.exists()) {
                "The removed ui/homescreen/ directory must not be recreated: $removedDirectory"
            }
        }
    }
}

tasks.named("preBuild") { dependsOn(guardRemovedHomeScreen) }
tasks.named("check") { dependsOn(guardRemovedHomeScreen) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.haze)
// Optional preset styling:
    implementation(libs.haze.materials)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric.core)
}