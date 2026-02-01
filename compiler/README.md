Arc3D Compiler
------
A compiler that compiles shaders written in Arc3D Shading Language into SPIR-V 1.0+, GLSL 3.30+,
and ESSL 3.00+, it is 6 to 70 times faster than glslang/shaderc/glslc.

Arc3D Shading Language is based on OpenGL Shading Language (GLSL) version 3.30.
It supports a subset of syntax from GLSL versions 4.00 through 4.60 while also incorporating some extended syntax features.

Arc3D Shading Language has no standard abbreviation or extension name.

### Dependencies
* fastutil

### Motivation

### Basic Usage
`ShaderCompiler` is the entry point of compiler. `parse` will
parse the source code (String) into a `TranslationUnit`. `generate*` will
generate code from `TranslationUnit` into backend code. You have to use
`Reference.reachabilityFence` to keep the code object alive.

The whole AST can be manipulated before code generation.

The compiler can generate big-endian SPIR-V.

#### Module
Arc3D has a concept of *module*. A module is a compilation unit that contains no entry function,
input/output variables, or interface blocks. A module can inherit from another module.
Every `TranslationUnit` must inherit from one module. The common ancestor of all modules is the
root module, which contains only built-in type declarations. A module includes a symbol table
and various declarations, such as system variables, function prototypes, and function definitions.
These are parsed and optimized once as a single unit, allowing them to be shared by all inheriting
modules and avoiding redundant parsing. A module is represented as a `ModuleUnit`,
you may parse a module via `parseModule`.

#### CompileOptions
`CompileOptions` specified the options during parsing and code generation.
See source code for details.

#### ShaderCaps
`ShaderCaps` specified the capabilities used for code generation. It generally depends on the capabilities
of your OpenGL context, OpenGL ES context, Vulkan device. If a capability is unavailable, the code generator
will transform it into an approximate form, though full equivalence is not guaranteed.
If the target environment lacks certain capabilities required by the generated backend code,
such code should be eliminated at the front‑end stage—the compiler will not perform the conversion automatically.
You will notice that Arc3D Engine subclasses ShaderCaps to generate Arc3D code programmatically.

### Differences from GLSL

##### Precision modifiers are not used.
`float`, `int`, and `uint` are always high
precision (32-bit). New types `min16float`, `min16int`, and `min16uint`
are mapped to SPIR-V Relaxed Precision and GLSL ES 3.0+ medium precision.
It allows operations to execute with  a relaxed precision of somewhere
between 16 and 32 bits. When these types are declared within an interface block,
their memory layout aligns with that of their high-precision equivalents.
High-precision types cannot be implicitly converted to their min16 versions
and must be cast via a constructor, except in the case of constants.

##### Vector types are named \<scalar type\>\<rows\>:
* bool2 bool3 bool4
* min16int2 min16int3 min16int4
* min16uint2 min16uint3 min16uint4
* int2 int3 int4
* uint2 uint3 uint4
* min16float2 min16float3 min16float4
* float2 float3 float4

##### Matrix types are named \<scalar type\>\<columns\>x\<rows\>:
* min16float2x2 min16float2x3 min16float2x4
* min16float3x2 min16float3x3 min16float3x4
* min16float4x2 min16float4x3 min16float4x4
* float2x2 float2x3 float2x4
* float3x2 float3x3 float3x4
* float4x2 float4x3 float4x4

##### All builtin variables must be explicitly declared:
```agsl
layout(builtin = position) out float4 SV_Position;
layout(vertex_id) in int SV_VertexID;
```
`builtin =` can be omitted.

Supported identifiers:
* position
* vertex_id
* instance_id
* vertex_index
* instance_index
* frag_coord
* front_facing
* sample_mask
* frag_depth
* num_workgroups
* workgroup_id
* local_invocation_id
* global_invocation_id
* local_invocation_index

### New Grammars
* Swizzle components
`0` and `1` may be present in swizzle components, for example:
```agsl
float2 localPos = ...
float4 pos = localPos.xy01; // float4(xy, 0, 1)
```
* Function modifiers
  + `__pure`: function has no side effects (should be internal only)
  + `inline`: force a function to always be inlined
  + `noinline`: force a function to never be inlined

### Limitations
* `default` label must be the last in a switch statement

### TODO
* More constant folding
* Dead code elimination
* Inlining
* Source minification

Additionally, many language features are not implemented yet in frontend.
Many features are not implemented in backend (both GLSL/ESSL and SPIR-V).
This largely depends on what features Arc3D itself is currently using.
