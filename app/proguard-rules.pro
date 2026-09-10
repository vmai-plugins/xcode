# Kotlin metadata is required by reflection-based serialization.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# kotlinx.serialization keeps generated serializers referenced only reflectively.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JGit and sshj resolve providers via the service loader.
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**
-keep class net.schmizz.sshj.** { *; }
-dontwarn net.schmizz.sshj.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class net.i2p.crypto.eddsa.** { *; }

# eddsa references sun.security.x509.X509Key on a JDK-only code path that Android
# never takes (EdDSAEngine.engineInitVerify falls back to it for non-eddsa keys).
# The class does not exist on Android, so R8 must be told not to warn rather than
# the reference being kept.
-dontwarn sun.security.x509.X509Key

# Never strip the structured error types: their names appear in diagnostics.
-keep class digital.vmstudio.code.core.common.error.** { *; }
