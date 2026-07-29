module icyllis.arc3d.image {
    requires transitive icyllis.arc3d.core;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    // Optional
    requires static jdk.incubator.vector;

    exports icyllis.arc3d.image;
    exports icyllis.arc3d.image.palette;
    exports icyllis.arc3d.image.j2d;
}