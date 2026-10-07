package net.minecraftforge.energy;

/**
 * Fixture stand-in under the name of MinecraftForge's standard energy store, one of the classes the transfer audit
 * certifies. Its bytes are the fixture's own, so they never match the reviewed 26.2 fingerprint.
 */
public class EnergyStorage {
	protected int energy;
	protected final int capacity;

	public EnergyStorage(int capacity) {
		this.capacity = capacity;
	}

	public int receiveEnergy(int maxReceive, boolean simulate) {
		int received = Math.min(capacity - energy, maxReceive);
		if (!simulate) energy += received;
		return received;
	}

	public int getEnergyStored() {
		return energy;
	}
}
