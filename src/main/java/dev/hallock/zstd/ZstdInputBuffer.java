package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_inBuffer;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.Objects;

public final class ZstdInputBuffer {
    private final MemorySegment input;

    public ZstdInputBuffer(SegmentAllocator allocator, MemorySegment buffer) {
        Objects.requireNonNull(allocator, "allocator");
        Objects.requireNonNull(buffer, "buffer");
        this.input = ZSTD_inBuffer.allocate(allocator);
        source(buffer);
        // For input buffers, 'size' is the total size of data in the buffer to be consumed.
        // 'pos' tracks how much has been consumed so far.
        size(buffer.byteSize());
        position(0);
    }

    MemorySegment input() {
        return input;
    }

    /// Equivalent to src
    public MemorySegment source() {
        return ZSTD_inBuffer.src(this.input);
    }

    /// Equivalent to src
    public void source(MemorySegment segment) {
        ZSTD_inBuffer.src(this.input, segment);
    }

    public long size() {
        return ZSTD_inBuffer.size(this.input);
    }

    public void size(long size) {
        ZSTD_inBuffer.size(this.input, size);
    }

    /// Equivalent to pos
    public long position() {
        return ZSTD_inBuffer.pos(this.input);
    }

    /// Equivalent to pos
    public void position(long position) {
        ZSTD_inBuffer.pos(this.input, position);
    }
}
