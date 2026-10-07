package org.spongepowered.asm.mixin.injection;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time stand-in for another Mixin fork's {@code @Redirect}, whose {@code at} is an array. Only javac sees it:
 * the test strips it from the fixture jar, as a mod jar never ships the annotation it was compiled against. Same
 * descriptor and retention as the real one, so the woven class file is what such a mod's jar holds.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Redirect {
	String[] method() default {};

	At[] at();

	int require() default -1;
}
