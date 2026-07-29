module icyllis.arc3d.opengl {
    requires transitive icyllis.arc3d.engine;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    // At least one of the following is required
    requires static org.lwjgl.opengl;
    requires static org.lwjgl.opengles;

    exports icyllis.arc3d.opengl;
}