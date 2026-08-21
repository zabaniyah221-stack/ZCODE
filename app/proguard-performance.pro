# ZCODE Performance perf1 — R8 konservatif tetapi nyata.
#
# Nama dipertahankan supaya Diagnostics/stacktrace masih dapat dibaca. R8 tetap
# boleh shrink dan optimize code yang tidak berada pada boundary tidak langsung.
-dontobfuscate

# Metadata yang dibaca oleh Android/WebView/reflection library.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# JavaScript CodeMirror memanggil method Kotlin berdasarkan nama/annotation.
-keepclassmembers,allowoptimization class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Python/Chaquopy memanggil public method bridge berdasarkan nama. Method ini
# tampak tidak terpakai bagi static analyzer tetapi merupakan API runtime.
-keep,allowoptimization class com.zaba.zcode.core.execution.TerminalBridge { public *; }
-keep,allowoptimization class com.zaba.zcode.core.packageengine.ResolveOperationBridge { public *; }

# Boundary Java/JNI Chaquopy dijaga luas pada perf1. Dipersempit hanya setelah
# mapping/configuration + device UAT membuktikan aman.
-keep class com.chaquo.python.** { *; }

# Entry point Android/Hilt/helper process. Manifest sebenarnya memberi reachability,
# tetapi safety keep kecil ini menjaga lifecycle perf1 saat R8 baru diadopsi.
-keep,allowoptimization class com.zaba.zcode.ZcodeApp { *; }
-keep,allowoptimization class com.zaba.zcode.MainActivity { *; }
-keep,allowoptimization class com.zaba.zcode.ZcodeRebirthActivity { *; }
