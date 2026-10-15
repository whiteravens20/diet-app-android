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

# The libraries the tests drive the app with, whose copy in the app they use.
-keep class androidx.activity.** { *; }
-keep class androidx.arch.core.** { *; }
-keep class androidx.concurrent.** { *; }
-keep class androidx.core.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.tracing.** { *; }
-keep class com.google.common.util.concurrent.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.datastore.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.test.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.json.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
