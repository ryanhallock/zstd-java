package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_outBuffer;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.Objects;

/**
 * An output buffer representation for Zstd streaming operations. Tracks the size and position
 * limits for data generation into a destination {@link MemorySegment}.
 *
 * <p>This object is a view over a native {@code ZSTD_outBuffer} struct that records the raw address
 * of the destination segment. Both the allocator scope used to create the buffer and the scope
 * backing the destination segment must remain alive for as long as the buffer is used in streaming
 * operations.
 *
 * <p>Instances are mutable, single-threaded views: streaming calls and the setters mutate the
 * underlying struct without synchronization. Confine each buffer to one thread at a time, or
 * provide external synchronization.
 */
public final class ZstdOutputBuffer {
  private final MemorySegment segment;
  private final MemorySegment destination;

  ZstdOutputBuffer(SegmentAllocator allocator, MemorySegment destination) {
    this(allocator, destination, destination.byteSize(), 0);
  }

  ZstdOutputBuffer(
      SegmentAllocator allocator, MemorySegment destination, long size, long position) {
    Objects.requireNonNull(allocator, "allocator");
    this.destination = Objects.requireNonNull(destination, "destination");
    Zstd.requireNative(destination, "destination");
    if (size < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    if (size > destination.byteSize()) {
      throw new IllegalArgumentException("size cannot be larger than destination segment size");
    }
    if (position < 0) {
      throw new IllegalArgumentException("position cannot be negative");
    }
    if (position > size) {
      throw new IllegalArgumentException("position cannot be larger than size");
    }
    final MemorySegment segment = ZSTD_outBuffer.allocate(allocator);
    // A heap allocator would produce a struct the native side cannot see.
    Zstd.requireNative(segment, "the ZSTD_outBuffer struct returned by allocator");
    ZSTD_outBuffer.dst(segment, destination);
    ZSTD_outBuffer.size(segment, size);
    ZSTD_outBuffer.pos(segment, position);
    this.segment = segment;
    super();
  }

  /**
   * Returns the backing destination memory segment.
   *
   * @return the destination memory segment
   */
  public MemorySegment destination() {
    return destination;
  }

  /**
   * Returns the size limit of the data to be written.
   *
   * @return the size limit
   */
  public long size() {
    return ZSTD_outBuffer.size(segment);
  }

  /**
   * Returns the current write position.
   *
   * @return the write position
   */
  public long position() {
    return ZSTD_outBuffer.pos(segment);
  }

  /**
   * Sets the size limit of the data to be written.
   *
   * @param newSize the new size limit
   * @throws IllegalArgumentException if {@code newSize} is negative, larger than the destination
   *     segment size, or smaller than the current {@link #position()}
   */
  public void size(long newSize) {
    if (newSize < 0) {
      throw new IllegalArgumentException("size cannot be negative");
    }
    if (newSize > destination.byteSize()) {
      throw new IllegalArgumentException("size cannot be larger than destination segment size");
    }
    if (newSize < position()) {
      throw new IllegalArgumentException("size cannot be smaller than the current position");
    }
    ZSTD_outBuffer.size(segment, newSize);
  }

  /**
   * Sets the write position.
   *
   * @param newPosition the new write position
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
    ZSTD_outBuffer.pos(segment, newPosition);
  }

  MemorySegment segment() {
    return segment;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ZstdOutputBuffer that)) return false;
    return segment.equals(that.segment) && destination.equals(that.destination);
  }

  @Override
  public int hashCode() {
    int result = segment.hashCode();
    result = 31 * result + destination.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "ZstdOutputBuffer[destination="
        + destination()
        + ", size="
        + size()
        + ", position="
        + position()
        + "]";
  }
}
