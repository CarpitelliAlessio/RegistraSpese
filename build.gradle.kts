plugins {
    alias(libs.plugins.android.application) apply false
    //alias(libs.plugins.kotlin.android) apply false

    // Il plugin di Google che avevamo aggiunto prima
    id("com.google.gms.google-services") version "4.4.2" apply false
}

