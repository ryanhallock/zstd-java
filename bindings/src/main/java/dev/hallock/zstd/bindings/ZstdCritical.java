package dev.hallock.zstd.bindings;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;

/**
 * Handwritten downcall handles for trivial zstd helper functions, linked with {@link
 * Linker.Option#critical(boolean)} so they skip the thread-state transition an ordinary downcall
 * pays.
 *
 * @implNote jextract cannot emit critical handles, hence this companion to {@link ZSTD_h}. Only
 *     functions that are O(1), never block, never upcall, and touch no Java heap memory may be
 *     linked this way. Compression and decompression entry points must never be added here: a
 *     critical downcall prevents the calling thread from reaching a GC safepoint for its whole
 *     duration.
 */
public final class ZstdCritical {

  // The size_t parameter in these descriptors is ZSTD_h.C_LONG, which the build post-processes to
  // a fixed 64-bit JAVA_LONG layout (see bindings/build.gradle.kts), so it is correct on LLP64 too.
  private static final MethodHandle IS_ERROR =
      Linker.nativeLinker()
          .downcallHandle(
              ZSTD_h.SYMBOL_LOOKUP.findOrThrow("ZSTD_isError"),
              FunctionDescriptor.of(ZSTD_h.C_INT, ZSTD_h.C_LONG),
              Linker.Option.critical(false));

  private static final MethodHandle GET_ERROR_CODE =
      Linker.nativeLinker()
          .downcallHandle(
              ZSTD_h.SYMBOL_LOOKUP.findOrThrow("ZSTD_getErrorCode"),
              FunctionDescriptor.of(ZSTD_h.C_INT, ZSTD_h.C_LONG),
              Linker.Option.critical(false));

  private static final MethodHandle GET_ERROR_NAME =
      Linker.nativeLinker()
          .downcallHandle(
              ZSTD_h.SYMBOL_LOOKUP.findOrThrow("ZSTD_getErrorName"),
              FunctionDescriptor.of(ZSTD_h.C_POINTER, ZSTD_h.C_LONG),
              Linker.Option.critical(false));

  private ZstdCritical() {}

  /**
   * Checks if a zstd function return value represents an error, via {@code ZSTD_isError}.
   *
   * @param result the return code from a zstd function
   * @return true if the result represents an error
   */
  public static boolean isError(long result) {
    try {
      return (int) IS_ERROR.invokeExact(result) != 0;
    } catch (Throwable t) {
      throw new AssertionError("ZSTD_isError downcall failed", t);
    }
  }

  /**
   * Returns the {@code ZSTD_ErrorCode} for an error return value, via {@code ZSTD_getErrorCode}.
   *
   * @param result the return code from a zstd function
   * @return the corresponding error code
   */
  public static int getErrorCode(long result) {
    try {
      return (int) GET_ERROR_CODE.invokeExact(result);
    } catch (Throwable t) {
      throw new AssertionError("ZSTD_getErrorCode downcall failed", t);
    }
  }

  /**
   * Returns the descriptive error name for a return value, via {@code ZSTD_getErrorName}.
   *
   * @param result the return code from a zstd function
   * @return the error name string
   */
  public static String getErrorName(long result) {
    try {
      return ((MemorySegment) GET_ERROR_NAME.invokeExact(result)).getString(0);
    } catch (Throwable t) {
      throw new AssertionError("ZSTD_getErrorName downcall failed", t);
    }
  }
}
