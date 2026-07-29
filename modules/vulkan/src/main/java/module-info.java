module icyllis.arc3d.vulkan {
    requires transitive org.lwjgl.vma;
    requires transitive org.lwjgl.vulkan;

    requires transitive icyllis.arc3d.engine;

    // Compile only
    requires static org.jetbrains.annotations;
    requires static org.jspecify;

    exports icyllis.arc3d.vulkan;
}