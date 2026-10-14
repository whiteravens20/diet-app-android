# R8 rules of the release build. The libraries bring their own rules; these are the app's.

# Hilt's generated builders are marked with Error Prone's @CanIgnoreReturnValue, an annotation
# for the compiler that is not on the runtime classpath and that nothing reads at run time.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
