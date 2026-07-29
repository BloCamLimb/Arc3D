module icyllis.arc3d.core {
    requires transitive java.desktop;

    requires transitive org.lwjgl;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    // Optional
    requires static org.lwjgl.zstd;

    exports icyllis.arc3d.core;
    exports icyllis.arc3d.core.util;
    exports icyllis.arc3d.core.zip;
}