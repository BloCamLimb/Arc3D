Arc3D Sketch
------
Sketch is 2D graphics engine (and API). 2D geometry, 3D (perspective) shading.

This subproject provides a wide range of geometry algorithms, rendering capabilities. Defines
geometry primitive, shader effect code, and also includes certain software rasterization algorithms.
It provides an implementation for converting, optimizing, or recording high‑level rendering instructions
(such as SVG‑like commands) into high‑level abstract calls that can be processed by different renderer backends
(e.g., CPU software and RHI), and manages resources such as images and surface layers.
The GPU implementation resides in other subprojects.

### Dependencies
* fastutil
* arc3d-core
* arc3d-compiler (optional, only when runtime effects are used)

### Goals
Sketch API intersects with Google Skia API, and you can consider it as a Java implementation of Skia API (modern parts).
One of the goals of Arc3D Sketch is to implement the majority of Skia’s APIs while keeping CPU-side overhead
as close as possible to that of Skia. Our aim is to shift as much work as feasible to the GPU, reducing
the load on the CPU, while ensuring that GPU-side overhead does not fall behind Skia Graphite.
In particular, in certain edge cases, Arc3D significantly outperforms Skia—especially Skia Ganesh—
by orders of magnitude (tens to hundreds of times faster). Moreover, our GPU backend addresses
many known bugs present in Skia Graphite. In any case, this is undoubtedly far superior to using
Skia bindings (such as skija).

Naturally, much of the code in Sketch has been improved upon or inspired by Skia, and follows its BSD‑style license.
The relevant copyright notices are retained in the headers of the corresponding files.

The CPU software backend only guarantees the implementation of most of the functions that needed by SVG,
and there has been almost no progress so far (except for path rasterization and font rasterization).

Arc3D Sketch has some interactivity with Java2D, especially the Java2D software rasterizer—Marlin renderer

### TODO
* Complete path rendering
* Skia runtime effects (maintain consistency with Android, Compose and Flutter)
* Image filters
* Mask filters
* Path effects
* CPU software backend
