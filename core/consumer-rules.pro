# LSParanoid Consumer Rules
# Keep Deobfuscator classes and their inner Chunk classes, but allow renaming/obfuscation

# Keep all Deobfuscator classes (allow obfuscation of class names)
-keep,allowobfuscation class **.Deobfuscator$** {
    # Keep getString method - called from obfuscated code
    public static java.lang.String getString(long);

    # Keep chunk loading infrastructure
    private static java.lang.ref.WeakReference[] chunks;
    private static int loadedUpTo;
    private static java.lang.String loadChunk(int);
    private static synchronized void ensureChunksLoaded(int);
}

# Keep Chunk inner classes and their DATA fields
-keep,allowobfuscation class **.Deobfuscator$**$Chunk* {
    static final byte[] DATA;
}

# Keep DeobfuscatorHelper methods used by generated code
-keep,allowobfuscation class com.androidacy.lsparanoid.DeobfuscatorHelper {
    public static java.lang.String getString(long, java.lang.String[]);
    public static java.lang.String[] loadChunksFromByteArray(byte[], long);
}

# Keep RandomHelper used by DeobfuscatorHelper
-keep,allowobfuscation class com.androidacy.lsparanoid.RandomHelper {
    public static long seed(long);
    public static long next(long);
}
