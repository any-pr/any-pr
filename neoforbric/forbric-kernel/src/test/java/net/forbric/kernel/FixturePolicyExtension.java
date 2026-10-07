/*
 * Copyright 2026 The Forbric Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package net.forbric.kernel;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import net.forbric.kernel.TestFixtures.Fixture;

/**
 * Holds every skip in the suite to {@value TestFixtures#POLICY}, not only the ones thrown by {@link TestFixtures}.
 *
 * <p>Several hundred tests still skip through a raw {@code assumeTrue}, which no policy reaches, so a machine told to
 * have every fixture could still pass by skipping them. Registered for the whole suite through
 * {@code META-INF/services} and {@code junit-platform.properties}: a skip whose message carries
 * {@code [fixture:<id>]} anywhere (JUnit puts "Assumption failed: " in front of an assumeTrue message) fails when
 * that kind is required, and an untagged skip, whose kind nobody wrote down, fails under any non-empty policy.
 * With no policy every skip passes through untouched.
 */
public final class FixturePolicyExtension implements TestExecutionExceptionHandler, LifecycleMethodExecutionExceptionHandler {
	private static final Pattern TAG = Pattern.compile("\\[fixture:([A-Za-z0-9_-]+)]");

	@Override
	public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		throw judge(throwable);
	}

	@Override
	public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		throw judge(throwable);
	}

	@Override
	public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		throw judge(throwable);
	}

	@Override
	public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		throw judge(throwable);
	}

	@Override
	public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		throw judge(throwable);
	}

	/** {@code thrown} itself, or the failure it becomes when it is a skip of a fixture this run requires. */
	static Throwable judge(Throwable thrown) {
		if (!(thrown instanceof TestAbortedException)) return thrown;
		Set<Fixture> required = TestFixtures.requiredFixtures();
		if (required.isEmpty()) return thrown;
		String message = thrown.getMessage() == null ? "" : thrown.getMessage();
		Matcher tag = TAG.matcher(message);
		// An unknown id is treated as untagged: a typo in a tag must not be what lets a required run skip.
		Fixture kind = tag.find() ? Fixture.byId(tag.group(1)) : null;
		if (kind != null && !required.contains(kind)) return thrown;
		String which = kind == null ? "an untagged skip counts as every fixture" : "fixture '" + kind.id() + "'";
		return new AssertionFailedError("skipped where " + which + " is required by " + TestFixtures.policySource()
				+ ": " + message, thrown);
	}
}
