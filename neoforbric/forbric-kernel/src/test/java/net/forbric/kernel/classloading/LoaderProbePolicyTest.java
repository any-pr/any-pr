/*
 * Copyright 2026 The Forbric Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.forbric.kernel.classloading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.forbric.api.Ecosystem;
import net.forbric.api.ForeignType;

/**
 * Covers which loader a guest class is allowed to see.
 *
 * <p>Traditional MinecraftForge is no longer an ecosystem this loader runs, so the only Forge-family marker left is
 * NeoForge's. A NeoForge mod must still be answered honestly about the loaders it did not ship as, and a Fabric
 * class asking about NeoForge's loader — or a NeoForge class asking about Fabric's — takes a branch it was never
 * built for if the answer is wrong.
 */
class LoaderProbePolicyTest {
	private static final String NEO_MARKER = ForeignType.FML_LOADER.binary(Ecosystem.NEOFORGE);

	@Test
	void theNeoForgeMarkerIsAProbe() {
		assertTrue(LoaderProbePolicy.isProbe(NEO_MARKER), NEO_MARKER + " must be a probe");
	}

	@Test
	void eachEcosystemMapsToItsOwnFamily() {
		assertEquals(LoaderProbePolicy.Family.FABRIC, LoaderProbePolicy.familyOf(Ecosystem.FABRIC));
		assertEquals(LoaderProbePolicy.Family.NEOFORGE, LoaderProbePolicy.familyOf(Ecosystem.NEOFORGE));
		assertNull(LoaderProbePolicy.familyOf(null), "an unowned jar has no family and probes as before");
	}

	@Test
	void aNeoForgeClassIsToldTheFabricLoaderIsAbsent() {
		assertTrue(LoaderProbePolicy.enabled(), "the test JVM never sets -Dforbric.loaderProbes=off");

		assertThrows(ClassNotFoundException.class, () -> LoaderProbePolicy.forName(
				"net.fabricmc.loader.api.FabricLoader", false, LoaderProbePolicyTest.class.getClassLoader(),
				LoaderProbePolicy.Family.NEOFORGE.name()),
				"this is the probe a NeoForge mod uses to pick its Fabric branch");
	}

	@Test
	void aFabricClassIsToldTheNeoForgeLoaderIsAbsent() {
		assertTrue(LoaderProbePolicy.enabled(), "the test JVM never sets -Dforbric.loaderProbes=off");
		ClassLoader here = LoaderProbePolicyTest.class.getClassLoader();
		String fabric = LoaderProbePolicy.Family.FABRIC.name();

		assertThrows(ClassNotFoundException.class,
				() -> LoaderProbePolicy.forName(NEO_MARKER, false, here, fabric));
	}

	@Test
	void aClassThatIsNotAProbeIsNeverHidden() {
		// The policy covers the canonical markers only. Anything else must resolve normally, or a mod that
		// genuinely links against a foreign type degrades into a link error instead of working.
		assertTrue(!LoaderProbePolicy.isProbe("net.neoforged.fml.ModList"));
		assertTrue(!LoaderProbePolicy.isProbe("java.lang.String"));

		assertEquals(String.class, assertDoesNotThrowClass(
				"java.lang.String", LoaderProbePolicy.Family.NEOFORGE.name()));
	}

	@Test
	void anUnownedJarSeesEveryProbeAsBefore() {
		// A universal jar carrying more than one manifest has no family; its probes are how it works out which
		// half of itself to run, and hiding either answer would break it. Its asking family is null.
		assertThrows(ClassNotFoundException.class, () -> LoaderProbePolicy.forName(
				"net.this.does.not.exist", false, LoaderProbePolicyTest.class.getClassLoader(), null),
				"a genuinely absent class still reports absent");
	}

	private static Class<?> assertDoesNotThrowClass(String name, String askingFamily) {
		try {
			return LoaderProbePolicy.forName(name, false, LoaderProbePolicyTest.class.getClassLoader(), askingFamily);
		} catch (ClassNotFoundException e) {
			throw new AssertionError(name + " must resolve normally", e);
		}
	}
}
