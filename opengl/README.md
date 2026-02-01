Arc3D OpenGL
------
OpenGL backend of Arc3D Engine

### Dependencies
* fastutil
* arc3d-engine

At least one of:
* lwjgl-opengl
* lwjgl-opengles

##### Natives:
At least one of:
* lwjgl-opengl (all platforms)
* lwjgl-opengles (all platforms)

Note: Arc3D actually uses `org.lwjgl.system.JNI` for most GL function calls,
so natives are not used too many

### Note
* For OpenGL ES, if base vertex is unavailable, gl_VertexID always begins at 0
* For OpenGL, regardless of the baseInstance value, gl_InstanceID always begins at 0