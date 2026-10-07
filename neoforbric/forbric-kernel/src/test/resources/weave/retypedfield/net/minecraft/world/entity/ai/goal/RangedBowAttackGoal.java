package net.minecraft.world.entity.ai.goal;

import java.util.List;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;

/**
 * A game-side target in its merged shape (hand-written; its name and field are a NARROW row of WidenedFieldTwinInjector):
 * the field is the carriers' Mob where vanilla's is Monster, so both calls in tick are Mob.lookAt — the vehicle's, which
 * vanilla also made through Mob, and the field's, which vanilla made through Monster.
 */
public class RangedBowAttackGoal {
	private final Mob mob;
	private final Entity target = new Entity();
	private final List<String> trace = target.seen;

	public RangedBowAttackGoal(Mob mob) {
		this.mob = mob;
	}

	/** Vanilla's tick, the part the guest anchors in: a ridden vehicle looks first, then the archer itself. */
	public String tick() {
		if (this.mob.getControlledVehicle() instanceof Mob vehicle) vehicle.lookAt(target, 30.0F, 30.0F);
		this.mob.lookAt(target, 30.0F, 30.0F);
		return String.join(",", trace);
	}

	/** The no-argument constructor the harness instantiates for its probe. */
	public RangedBowAttackGoal() {
		this(new Monster("probe"));
	}

	/**
	 * The harness probe: an archer that is a Monster, one riding a Mob, and one the carriers' widening lets in — a Mob
	 * that is no Monster, which vanilla's goal could never hold.
	 */
	public String probe() {
		return "monster=" + new RangedBowAttackGoal(new Monster("archer")).tick()
				+ " riding=" + new RangedBowAttackGoal(new Monster("archer").riding(new Mob("horse"))).tick()
				+ " widened=" + new RangedBowAttackGoal(new PathfinderMob("golem")).tick();
	}
}
