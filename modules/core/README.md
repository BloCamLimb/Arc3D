Arc3D Core
------
The base project used by all other subprojects, except for the Arc3D Compiler.

### Dependencies
* fastutil
* lwjgl (core)

##### Natives:
* lwjgl (all platforms)

### Features
* MathUtil, fast & extended math
* Vector and matrix math
* Quaternion
* Float/integer rectangle (2D bounding box)
* Pixel map and pixel memory container
* Color space definition, color space conversion
* Pixel format conversion, alpha type (premul / unpremul) conversion
* FP16 definition
* Atomic RefCnt
* Rectangle packer (for texture atlas generation)
* GIF decoder (and LZW decoder)

Note, all operations are executed on the CPU. The GPU implementations of certain features are distributed across other subprojects.

### TODO
* Three-pass box blur for single channel data
* Single-channel SDF generation (8SSEDT from rasterized data; high-quality result directly from vector data, handling self-intersection)
