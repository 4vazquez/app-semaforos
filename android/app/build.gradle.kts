import java.util.Properties

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

// Firma estable del build debug: si existe keystore/keystore.properties
// (fuera del repo, en la raíz del proyecto), el debug se firma con ese
// keystore. Sin él (p. ej. un clon fresco), Gradle usa la firma debug por
// defecto con un aviso — pero esa firma cambia por máquina y ROMPE las
// actualizaciones en el teléfono ("conflicto con un paquete", nov 2026:
// v0.3.1 y v0.3.2 se firmaron con certificados debug distintos).
val keystorePropertiesFile = rootProject.file("../keystore/keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
}

android {
    namespace = "com.alan.semaforosmx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alan.semaforosmx"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "1.1.13"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Configuración de Supabase + Google Sign-In (v1.1): se lee de
        // android/local.properties (nunca se sube a git). Sin estos
        // valores la app funciona en modo local anónimo.
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { localProps.load(it) }
        }
        buildConfigField(
            "String", "SUPABASE_URL",
            "\"${localProps.getProperty("SUPABASE_URL", "")}\""
        )
        buildConfigField(
            "String", "SUPABASE_ANON_KEY",
            "\"${localProps.getProperty("SUPABASE_ANON_KEY", "")}\""
        )
        buildConfigField(
            "String", "GOOGLE_SERVER_CLIENT_ID",
            "\"${localProps.getProperty("GOOGLE_SERVER_CLIENT_ID", "")}\""
        )
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("stableDebug") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("stableDebug")
            } else {
                logger.warn("keystore.properties no encontrado: el build debug usará la firma debug por defecto (cambia por máquina; las actualizaciones en el teléfono pueden fallar con 'conflicto con un paquete').")
            }
            // Actualizador propio ACTIVO: la app se distribuye sideload
            // (APK directo / GitHub Releases) y se actualiza sola.
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
        }
        release {
            // Actualizador propio DESACTIVADO: Google Play PROHÍBE que
            // una app de Play se actualice desde fuera de Play. En el
            // build de Play la actualización la maneja Play (In-App
            // Updates API). No cambiar a true (ver SPEC.md §13).
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
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
        // BuildConfig genera SELF_UPDATE_ENABLED (flag del actualizador
        // propio, distinto por buildType). AGP 8.x lo trae apagado.
        buildConfig = true
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

    // Login con Google (v1.1): Credential Manager + Google ID.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Sesión cifrada en el teléfono (v1.1).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
