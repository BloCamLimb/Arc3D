module icyllis.arc3d.engine {
    requires transitive org.slf4j;

    requires transitive icyllis.arc3d.compiler;
    requires transitive icyllis.arc3d.core;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    exports icyllis.arc3d.engine;
    exports icyllis.arc3d.engine.mock;
}