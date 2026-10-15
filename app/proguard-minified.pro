# R8 rules of the minified build type alone, which the screens' tests run on. A release build
# does not take them: they keep what the tests call in the app and in the libraries the tests
# share with it, so that what R8 does to the rest of the app is what a release ships.

# What the tests call in the app.
-keep class net.whiteravens.dietapp.R { *; }
-keep class net.whiteravens.dietapp.R$* { *; }
-keep class net.whiteravens.dietapp.BuildConfig { *; }
-keep class net.whiteravens.dietapp.MainActivity { *; }
-keep class net.whiteravens.dietapp.ui.common.LabelsKt { *; }
-keep class net.whiteravens.dietapp.data.remote.ApiException { *; }
-keep class net.whiteravens.dietapp.data.remote.ApiErrorIssueDto { *; }
-keep class net.whiteravens.dietapp.data.auth.AesGcmSealer { *; }
-keep class net.whiteravens.dietapp.data.auth.KeystoreSealer { *; }
-keep class net.whiteravens.dietapp.ui.screens.profiles.WeightUnit { *; }
-keep class net.whiteravens.dietapp.ui.screens.profiles.WeightUnit$Companion { *; }

# The libraries the tests share with the app, whose copy in the app they use: AndroidX (Compose,
# the test runner's own), Kotlin's, Guava's and the injection annotations Espresso builds on.
# What stays under R8 is the app's own code, Retrofit and Dagger, where R8 breaks releases.
-keep class androidx.** { *; }
-keep class com.google.common.** { *; }
-keep class javax.inject.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jspecify.** { *; }
