# OkHttp / Okio use reflection for platform detection; keep them intact.
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Jsoup does HTML parsing reflectively in places.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Room entities/DAOs are annotation-processed at compile time, but keep the
# model classes themselves since QueueRepository/DownloadService serialize
# them by field access.
-keep class com.invictus.xmd.core.** { *; }

# Kotlin coroutines internals occasionally trip up R8 without this.
-dontwarn kotlinx.coroutines.**

# libtorrent4j: its SWIG-generated JNI glue is loaded/called by name from
# native code, so R8 can't see those references -- without this keep rule a
# release build crashes at runtime the moment TorrentEngine touches the
# session (works fine in debug, which is why this is easy to miss).
-keep class org.libtorrent4j.** { *; }
-keep class org.libtorrent4j.swig.** { *; }
-dontwarn org.libtorrent4j.**

# youtubedl-android/ffmpeg (yt-dlp support, Full flavor only): bundles a
# python interpreter + native ffmpeg/ffprobe binaries and unpacks/invokes
# them via reflection-heavy internal plumbing. Same class of bug as
# libtorrent4j above -- without this keep rule, tapping "Install" in
# Settings crashes the app the moment YoutubeDL.init()/FFmpeg.init() runs
# in a release (minified) build, even though it works fine in debug.
-keep class com.yausername.** { *; }
-dontwarn com.yausername.**

# Transitive dependency of youtubedl-android: it unpacks the bundled
# python/yt-dlp archives with Apache Commons Compress, whose
# ExtraFieldUtils reflectively Class.newInstance()'s its zip extra-field
# classes (AsiExtraField, X5455_ExtendedTimestamp, ...). Nothing calls
# their constructors directly, so R8 strips them as "unused" -- the
# install then crashes with NoClassDefFoundError on an obfuscated class
# name (e.g. "V4.f") the moment Commons Compress tries to instantiate one.
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.compress.**

# AGP 9 turns on R8 strict full mode for keep rules: a bare `-keep class X`
# no longer keeps X's constructors. Room instantiates the generated
# AppDatabase_Impl reflectively through its no-arg constructor, so pin it
# explicitly instead of relying on the library's consumer rules.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
