# Project-specific R8 rules go here.

# ML Kit finds its component registrars by class name from manifest <meta-data> keys, which R8 can't see.
# Without this, TextRecognition.getClient() crashes in release builds.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
