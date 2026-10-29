import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lunara.sleep"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lunara.sleep"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // API adresi BuildConfig'e gömülür.
        //  - Release: findsleepdoctors.com üzerinden yayınlanan Lunara API
        //  - Debug:   -PapiBaseUrl=... ile (varsayılan) emülatörde 10.0.2.2
        val apiBaseUrl = project.findProperty("apiBaseUrl")
            ?: "https://findsleepdoctors.com"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            // Imzalama bilgileri: ortam degiskeni > -P ile verilen property >
            // yerel keystore.properties dosyasi. hicbiri repoya girmez.
            val localProps = Properties().apply {
                val f = rootProject.file("keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun prop(name: String): String? =
                System.getenv(name) ?: localProps.getProperty(name)
                    ?: (project.findProperty(name) as String?)

            val ks = prop("LUNARA_STORE_FILE")
            if (!ks.isNullOrBlank()) {
                // Once mutlak yol, sonra proje kokunden goreceli yol.
                val storePath = file(ks).takeIf { it.exists() }
                    ?: rootProject.file(ks).takeIf { it.exists() }
                    ?: rootProject.projectDir.parentFile?.let { File(it, ks) }
                        ?.takeIf { it.exists() }
                if (storePath != null) {
                    storeFile = storePath
                    storePassword = prop("LUNARA_STORE_PASSWORD")
                    keyAlias = prop("LUNARA_KEY_ALIAS") ?: "lunara"
                    keyPassword = prop("LUNARA_KEY_PASSWORD")
                    logger.lifecycle("[lunara] release signing: ${storePath.absolutePath}")
                } else {
                    logger.lifecycle(
                        "[lunara] release signing disabled — keystore not found for '$ks'"
                    )
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val sc = signingConfigs.findByName("release")
            if (sc != null && sc.storeFile != null) {
                signingConfig = sc
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            // Emulator: host makinedeki API'ye ulasmak icin 10.0.2.2.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${project.findProperty("debugApiBaseUrl") ?: "http://10.0.2.2:4000"}\"",
            )
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
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Yalnızca cache'de hazır olan sürümler — build ortamı çevrimdışı
    // çalışabilir ve sürüm uyuşmazlığı derlemeyi kırabilir.
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
