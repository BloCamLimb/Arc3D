module icyllis.arc3d.granite {
    requires transitive icyllis.arc3d.engine;
    requires transitive icyllis.arc3d.sketch;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    exports icyllis.arc3d.granite;
    exports icyllis.arc3d.granite.geom;
    exports icyllis.arc3d.granite.shading;
    exports icyllis.arc3d.granite.task;
    exports icyllis.arc3d.granite.tessellate;
}