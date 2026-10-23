package fixture.transferaudit;

/** The same store under a name the audit does not review: the run's control. */
public class UnreviewedStorage {
	protected int energy;
	protected final int capacity;

	public UnreviewedStorage(int capacity) {
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
