# Applied to every app that uses this library.
#
# ML Kit looks some of its own classes up by name. With R8 shrinking on
# (the default for an Expo release build), reading any picture fails inside
# ML Kit with a NullPointerException unless these are kept.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face_bundled.** { *; }
