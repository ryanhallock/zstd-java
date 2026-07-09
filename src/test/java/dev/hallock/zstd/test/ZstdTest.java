package dev.hallock.zstd.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Custom annotation for Zstd tests. Enables parameter injection of a {@link dev.hallock.zstd.Zstd}
 * instance.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ZstdParameterResolver.class)
public @interface ZstdTest {}
