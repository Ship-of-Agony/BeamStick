# BeamStick Launcher ProGuard / R8 Regeln
-keep class com.example.beamsticklauncher.UsbDrive { *; }
-keep class com.example.beamsticklauncher.MediaProperty { *; }
-keep class com.example.beamsticklauncher.MediaCategory { *; }

-dontwarn android.media.**
-keepattributes InnerClasses, Signature, *Annotation*