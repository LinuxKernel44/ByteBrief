# R8 rules for ByteBrief. Library consumer rules (Room, WorkManager, OkHttp, Compose) are applied automatically.

# JNI: native code looks up these classes and methods by name (also declared in :llama's consumer rules).
-keep class app.bytebrief.llama.** { *; }

# Enum names are persisted in settings (SharedPreferences) and read with valueOf().
-keepclassmembers enum app.bytebrief.source.hn.HackerNewsSource$Feed { *; }
-keepclassmembers enum app.bytebrief.source.github.TrendingSource$Period { *; }

# Room entities are read by generated code; keep field names for readable crash reports and schema export parity.
-keepclassmembers class app.bytebrief.data.db.** { public <fields>; }

# Readable stack traces for crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optional dependencies referenced by OkHttp/Okio that are not on Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
