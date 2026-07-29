module icyllis.arc3d.compiler {
    requires transitive icyllis.arc3d.core;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    // Compile only
    requires static org.lwjgl.spvc;

    exports icyllis.arc3d.compiler;
    exports icyllis.arc3d.compiler.analysis;
    exports icyllis.arc3d.compiler.glsl;
    exports icyllis.arc3d.compiler.lex;
    exports icyllis.arc3d.compiler.spirv;
    exports icyllis.arc3d.compiler.transform;
    exports icyllis.arc3d.compiler.tree;
}