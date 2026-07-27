### PNG Decoding Benchmarks

> Note: All benchmark scores include I/O overhead.
> - `_buffer`: Opens a `FileChannel` and reads the entire file into a direct `ByteBuffer` before decoding. The score includes the full file-loading time.
> - `_stream`: Reads directly from a `FileChannel` using a fixed 8KB stream buffer without backtracking.
> - `_file`: Reads from a `RandomAccessFile` (because Java ImageIO does not support streaming).

- stb_image and spng are compiled with O3.
- stb_image uses stb_zlib; spng uses miniz; arc3d and imageio use zlib

test images are compressed using zlib compression level 6

#### Case 1: 1368x2189, RGB_ALPHA, 8bpc, 4bpp (SUB filter only)
| Decoder | Mode | Cnt | Score (ops/s) | Error |
| :--- | :--- | :--- | :--- | :--- |
| **arc3d_decode_buffer** | thrpt | 5 | **18.824** | ± 0.391 |
| **arc3d_decode_stream** | thrpt | 5 | 18.382 | ± 0.305 |
| spng_decode_buffer | thrpt | 5 | 18.053 | ± 0.368 |
| stb_image_decode_stream | thrpt | 5 | 15.555 | ± 0.244 |
| stb_image_decode_buffer | thrpt | 5 | 15.452 | ± 0.211 |
| imageio_decode_file | thrpt | 5 | 10.570 | ± 1.121 |

#### Case 2: 1920x1080, RGB_ALPHA, 8bpc, 4bpp (Mixed filters, mostly PAETH)
| Decoder | Mode | Cnt | Score (ops/s) | Error |
| :--- | :--- | :--- | :--- | :--- |
| **arc3d_decode_buffer** | thrpt | 5 | **20.549** | ± 0.564 |
| **arc3d_decode_stream** | thrpt | 5 | 19.934 | ± 0.487 |
| spng_decode_buffer | thrpt | 5 | 18.500 | ± 0.295 |
| stb_image_decode_stream | thrpt | 5 | 17.080 | ± 0.185 |
| stb_image_decode_buffer | thrpt | 5 | 16.952 | ± 0.118 |
| imageio_decode_file | thrpt | 5 | 9.554 | ± 0.333 |

#### Case 3: 1054x1492, RGB, 8bpc, 3bpp (Mixed filters, mostly PAETH)
| Decoder | Mode | Cnt | Score (ops/s) | Error |
| :--- | :--- | :--- | :--- | :--- |
| **arc3d_decode_buffer** | thrpt | 5 | **27.769** | ± 0.505 |
| **arc3d_decode_stream** | thrpt | 5 | 27.171 | ± 0.523 |
| spng_decode_buffer | thrpt | 5 | 26.771 | ± 0.241 |
| stb_image_decode_stream | thrpt | 5 | 23.088 | ± 0.286 |
| stb_image_decode_buffer | thrpt | 5 | 22.768 | ± 0.699 |
| imageio_decode_file | thrpt | 5 | 12.607 | ± 2.472 |

#### Case 4: 1920x1080, RGB, 16bpc, 6bpp (PAETH filter only)
| Decoder | Mode | Cnt | Score (ops/s) | Error |
| :--- | :--- | :--- | :--- | :--- |
| **arc3d_decode_buffer_zlib_ng** | thrpt | 5 | **25.946** | ± 1.293 |
| **arc3d_decode_stream_zlib_ng** | thrpt | 5 | 25.118 | ± 1.014 |
| **arc3d_decode_buffer** | thrpt | 5 | **17.913** | ± 0.623 |
| **arc3d_decode_stream** | thrpt | 5 | 17.593 | ± 0.708 |
| stb_image_decode_stream | thrpt | 5 | 12.999 | ± 0.157 |
| stb_image_decode_buffer | thrpt | 5 | 12.923 | ± 0.192 |
| spng_decode_buffer | thrpt | 5 | 7.900 | ± 0.087 |
| imageio_decode_file | thrpt | 5 | 5.822 | ± 0.077 |
