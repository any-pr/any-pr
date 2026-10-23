package net.minecraft.core;

import fixture.fabricfreezehook.Trace;

/** Hand-written stand-in, not game code: a registry that refuses a new key once frozen, with the game's message. */
public final class MappedRegistry {
	private final String key;
	private boolean frozen;

	public MappedRegistry(String key) {
		this.key = key;
	}

	public void register(String entry) {
		if (frozen) {
			throw new IllegalStateException("Registry is already frozen (trying to add key ResourceKey[" + key + " / "
					+ entry + "])");
		}
		Trace.add(key + "+" + entry);
	}

	public void freeze() {
		frozen = true;
	}

	/** What the kernel's registration window does to the root before the Fabric mains run. */
	public void unfreeze() {
		frozen = false;
	}
}
