package dev.hallock.zstd;

import dev.hallock.zstd.bindings.ZSTD_outBuffer;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.Objects;

public final class ZstdOutputBuffer {
    private final MemorySegment output;

    public ZstdOutputBuffer(SegmentAllocator allocator, MemorySegment buffer) {
        Objects.requireNonNull(allocator, "allocator");
        Objects.requireNonNull(buffer, "buffer");
        this.output = ZSTD_outBuffer.allocate(allocator);
        destination(buffer);
        size(buffer.byteSize());
        position(0);
    }

    MemorySegment output() {
        return output;
    }

    /// Equivalent to dst
    public MemorySegment destination() {
        return ZSTD_outBuffer.dst(this.output);
    }
    
    public void destination(MemorySegment segment) {
        ZSTD_outBuffer.dst(this.output, segment);
    }

    public long size() {
        return ZSTD_outBuffer.size(this.output);
    }

    public void size(long size) {
        ZSTD_outBuffer.size(this.output, size);
    }

    /// Equivalent to pos
    public long position() {
        return ZSTD_outBuffer.pos(this.output);
    }

    public void position(long position) {
        ZSTD_outBuffer.pos(this.output, position);
    }
}
