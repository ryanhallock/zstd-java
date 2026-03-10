module dev.hallock.zstd.test {
    requires dev.hallock.zstd;
    requires dev.hallock.zstd.bindings;
    requires org.junit.jupiter.api;
    requires org.junit.jupiter.params;

    exports dev.hallock.zstd.test;
    opens dev.hallock.zstd.test to org.junit.platform.commons;
}