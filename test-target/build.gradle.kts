plugins {
    id("com.android.application")
}

android {
    namespace = "fr.tempolock.testtarget"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.tempolock.testtarget"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}
