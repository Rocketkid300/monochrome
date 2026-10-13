# R8 rules for the release build.
#
# Retrofit, OkHttp, kotlinx.serialization, Hilt, Coil and Media3 ship their own consumer
# rules. A release build with only those succeeded and was inspected (dexdump + mapping/seeds):
# HifiApi kept its Retrofit annotations and generic Signatures (needed for suspend return
# types), and the serializers the app references were kept.
-keepattributes *Annotation*

# HifiApi returns kotlinx.serialization.json.JsonElement, and Retrofit's kotlinx converter finds
# its serializer reflectively, by the literal names `Companion` and `serializer()`. The library's
# consumer rules keep these today (see seeds.txt); this states the dependency explicitly so a
# library rule change cannot silently break every API call at runtime.
-keepclassmembers class kotlinx.serialization.json.JsonElement {
    public static ** Companion;
}
-keepclassmembers class kotlinx.serialization.json.JsonElement$Companion {
    public kotlinx.serialization.KSerializer serializer();
}
