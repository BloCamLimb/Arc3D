module icyllis.arc3d.sketch {
    requires transitive icyllis.arc3d.core;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    exports icyllis.arc3d.sketch;
    exports icyllis.arc3d.sketch.effects;
    exports icyllis.arc3d.sketch.image;
    exports icyllis.arc3d.sketch.j2d;
    exports icyllis.arc3d.sketch.shaders;
}