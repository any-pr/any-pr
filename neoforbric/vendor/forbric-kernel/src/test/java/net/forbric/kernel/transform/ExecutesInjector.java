package net.forbric.kernel.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class that defines an injector's output and runs it, as opposed to reading the emitted bytecode back.
 *
 * <p>{@code InjectorExecutionCensusTest} reads this from the compiled test classes and counts each named injector as
 * executed, so only put it on a class that really does: the census also rejects a class that names an injector here
 * but never references it in code, or that defines no class at all. Runtime retention because the census reads the
 * class file's visible annotations.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ExecutesInjector {
	/** The injectors whose transformed output this test class loads and calls. */
	Class<? extends ClassTransformer>[] value();
}
