module icyllis.arc3d.core {
    requires java.desktop;
    requires java.xml;

    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    requires org.lwjgl;

    requires static jdk.incubator.vector;
    requires static org.lwjgl.zstd;

    exports icyllis.arc3d.core;
    exports icyllis.arc3d.core.image;
    exports icyllis.arc3d.core.util;
    exports icyllis.arc3d.core.palette;
    exports icyllis.arc3d.core.zip;
}