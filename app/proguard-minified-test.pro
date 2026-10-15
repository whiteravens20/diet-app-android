# R8 rules of the screens' tests' own package when they run on the minified build type: the
# tests and what they bring are kept whole; only their references into the app follow R8.
-dontobfuscate
-dontwarn **
-keep class net.whiteravens.dietapp.** { *; }
-keep class org.junit.** { *; }
-keep class junit.** { *; }
-keep class org.hamcrest.** { *; }
-keep class androidx.test.** { *; }
-keep class okhttp3.mockwebserver.** { *; }
-keep class mockwebserver3.** { *; }
