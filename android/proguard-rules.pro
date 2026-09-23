# openfuel release rules. Room, DataStore, MapLibre and kotlinx-coroutines ship
# their own consumer rules; kotlinx.serialization is only used through its tree
# API (no @Serializable classes), so it needs nothing either.

# Keep line numbers in crash reports, without the source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# MapLibre loads these through JNI by name.
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
