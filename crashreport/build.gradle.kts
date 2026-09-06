plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.publish)
}

android {
    namespace = "dikiz.app.crashreport"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 31

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(
        groupId = "io.github.otangid",
        artifactId = "CrashReportCompose",
        version = "1.1"
    )

    pom {
        name.set("CrashReportCompose Library")
        description.set("A library for capturing and displaying a Compose-based crash report UI.")
        inceptionYear.set("2026")
        url.set("https://github.com/otangid/CrashReportCompose")

        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }

        developers {
            developer {
                id.set("otangid")
                name.set("Diki Zulkarnaen")
                email.set("dikizulkarnaen021@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/otangid/CrashReportCompose")
            connection.set("scm:git:git://github.com/otangid/CrashReportCompose.git")
            developerConnection.set("scm:git:ssh://github.com/otangid/CrashReportCompose.git")
        }
    }
}