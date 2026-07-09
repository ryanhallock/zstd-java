package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_inBuffer;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.Objects;

/**
 * An input buffer representation for Zstd streaming operations. Tracks the size and position limits
 * for data consumption from a source {@link MemorySegment}.
 *
 * <p>This object is a view over a native {@code ZSTD_inBuffer} struct that records the raw address
 * of the source segment. Both the allocator scope used to create the buffer and the scope backing
 * the source segment must remain alive for as long as the buffer is used in streaming operations.
 *
 * <p>Instances are mutable, single-threaded views: streaming calls and the setters mutate the
 * underlying struct without synchronization. Confine each buffer to one thread at a time, or
 * provide external synchronization.
 */
public final class ZstdInputBuffer {
  private final MemorySegment segment;
  private final MemorySegment source;

  ZstdInputBuffer(SegmentAllocator allocator, MemorySegment source) {
    this(allocator, source, source.byteSize(), 0);
  }

  ZstdInputBuffer(SegmentAllocator allocator, MemorySegment source, long size, long position) {
    Objects.requireNonNull(allocator, "allocator");
    this.source = Objects.requireNonNull(source, "source");
    Zstd.requireNative(source, "source");
    if (size < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    if (size > source.byteSize()) {
      throw new IllegalArgumentException("size cannot be larger than source segment size");
    }
    if (position < 0) {
      throw new IllegalArgumentException("position cannot be negative");
    }
    if (position > size) {
      throw new IllegalArgumentException("position cannot be larger than size");
    }
    final MemorySegment segment = ZSTD_inBuffer.allocate(allocator);
    // A heap allocator would produce a struct the native side cannot see.
    Zstd.requireNative(segment, "the ZSTD_inBuffer struct returned by allocator");
    ZSTD_inBuffer.src(segment, source);
    ZSTD_inBuffer.size(segment, size);
    ZSTD_inBuffer.pos(segment, position);
    this.segment = segment;
    super();
  }

  /**
   * Returns the backing source memory segment.
   *
   * @return the source memory segment
   */
  public MemorySegment source() {
    return source;
  }

  /**
   * Returns the size limit of the data to be consumed.
   *
   * @return the size limit
   */
  public long size() {
    return ZSTD_inBuffer.size(segment);
  }

  /**
   * Returns the current read position.
   *
   * @return the read position
   */
  public long position() {
    return ZSTD_inBuffer.pos(segment);
  }

  /**
   * Sets the size limit of the data to be consumed.
   *
   * @param newSize the new size limit
   * @throws IllegalArgumentException if {@code newSize} is negative, larger than the source segment
   *     size, or smaller than the current {@link #position()}
   */
  public void size(long newSize) {
    if (newSize < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    if (newSize > source.byteSize()) {
      throw new IllegalArgumentException("size cannot be larger than source segment size");
    }
    if (newSize < position()) {
      throw new IllegalArgumentException("size cannot be smaller than the current position");
    }
    ZSTD_inBuffer.size(segment, newSize);
  }

  /**
   * Sets the read position.
   *
   * @param newPosition the new read position
   * @throws IllegalArgumentException if {@code newPosition} is negative or larger than the current
   *     {@link #size()}
   */
  public void position(long newPosition) {
    if (newPosition < 0) {
      throw new IllegalArgumentException("position cannot be negative");
    }
    if (newPosition > size()) {
      throw new IllegalArgumentException("position cannot be larger than size");
    }
    ZSTD_inBuffer.pos(segment, newPosition);
  }

  MemorySegment segment() {
    return segment;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ZstdInputBuffer that)) return false;
    return segment.equals(that.segment) && source.equals(that.source);
  }

  @Override
  public int hashCode() {
    int result = segment.hashCode();
    result = 31 * result + source.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ZstdInputBuffer[source="
        + source()
        + ", size="
        + size()
        + ", position="
        + position()
        + "]";
  }
}
