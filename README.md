# Arc3D
![Maven Central Version](https://img.shields.io/maven-central/v/dev.icyllis/arc3d-core)
### Overview
Arc3D is a modern graphics engine, which is written purely in Java 17 and based on LWJGL 3.4.1  

Compatible with headless JDK builds `--enable-headless-only`.  
Compatible with LWJGL from 3.3.1 to 3.4.2. But OpenGL ES backend and Vulkan backend require LWJGL 3.3.6 or above.

At runtime, Java 26 or above is recommended, Java 17 is the minimum.

If you're looking for a framework that already fully integrates Arc3D, check out
[ModernUI](https://github.com/BloCamLimb/ModernUI).

### Dependencies
* lwjgl (core)

If arc3d-engine is used, at least one of the following to provide 3D API binding:
+ lwjgl-opengl
+ lwjgl-opengles
+ lwjgl-vulkan, lwjgl-vma


### Adding to your project
```groovy
repositories {
    mavenCentral()
}
dependencies {
    // Add modules as needed
    api "dev.icyllis:arc3d-core:${arc3d_version}"
    api "dev.icyllis:arc3d-sketch:${arc3d_version}"
    api "dev.icyllis:arc3d-compiler:${arc3d_version}"
    api "dev.icyllis:arc3d-engine:${arc3d_version}"
    api "dev.icyllis:arc3d-granite:${arc3d_version}"
    api "dev.icyllis:arc3d-vulkan:${arc3d_version}"
    api "dev.icyllis:arc3d-opengl:${arc3d_version}"

    // Remember to add LWJGL modules as described above, depending on your needs
    //api "org.lwjgl:lwjgl:${lwjgl_version}"
    //api "org.lwjgl:lwjgl-opengl:${lwjgl_version}"
    //api "org.lwjgl:lwjgl-opengles:${lwjgl_version}"
    //api "org.lwjgl:lwjgl-vma:${lwjgl_version}"
    //api "org.lwjgl:lwjgl-vulkan:${lwjgl_version}"
    // and LWJGL natives
}
```

### Copyright Notice
This project is licensed under LGPL-3.0-or-later.  
Additional copyright notice are included in the header of specific files.
