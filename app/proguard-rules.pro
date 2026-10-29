# R8 rules for the host. Upstream v2rayNG ships with minify off, so its code was never written
# for obfuscation: Gson builds the Xray config from its model classes by reflection, and the
# Go bindings are called from native code. Keep both whole; our code and tgui still shrink.

# v2rayNG (as the :v2rayng library) and the gomobile bindings in libv2ray.aar.
-keep class com.v2ray.ang.** { *; }
-keep class libv2ray.** { *; }
-keep class go.** { *; }

# Gson reads generic field types and @SerializedName at runtime.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
