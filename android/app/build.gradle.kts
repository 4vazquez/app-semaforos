plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// collection-ktx 1.2.0 duplica las clases *Kt (ArrayMapKt, LruCacheKt,
// etc.) que collection 1.4.0 ya incluye: en 1.4.0 las extensiones -ktx se
// fusionaron en el artefacto principal y el módulo -ktx quedó obsoleto.
// Sin esta exclusión, checkDebugDuplicateClasses falla. Alguna dependencia
// transitiva (AndroidX) todavía arrastra collection-ktx 1.2.0.
configurations.all {
    exclude(group = "androidx.collection", module = "collection-ktx")
}

android {
    namespace = "com.alan.semaforosmx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alan.semaforosmx"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    }

    packaging {
        resources {
            // Las librerías AndroidX multiplaforma (lifecycle-common,
            // collection, annotation, etc.) embarcan los mismos ficheros de
            // metadatos Kotlin/Native (.knm) y el mismo `manifest` bajo
            // commonMain/ y nonJvmMain/; el merge de recursos falla por
            // duplicado. Estos ficheros solo los usa el tooling KMP, nunca
            // la app en Android, así que se excluyen todos del APK.
            // (Entradas explícitas: el glob `*Main/**` no excluyó
            // nonJvmMain en AGP 8.5.2, comportamiento no documentado.)
            excludes += "commonMain/**"
            excludes += "nonJvmMain/**"
            excludes += "nativeMain/**"
            excludes += "jsMain/**"
            excludes += "jvmMain/**"
            excludes += "androidMain/**"
            excludes += "metadataMain/**"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // AndroidX + Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Mapa: OpenStreetMap vía osmdroid — sin llave de API ni facturación.
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    // Red: cliente REST de Supabase (Retrofit + OkHttp + Moshi).
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Anuncios: banner AdMob (ID de prueba en el MVP).
    implementation("com.google.android.gms:play-services-ads:23.4.0")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
