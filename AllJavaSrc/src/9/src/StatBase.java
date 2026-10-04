package net.minecraft.src;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class StatBase {
	public final int statId;
	public final String statName;
	public boolean isIndependent;
	public String statGuid;
	private final IStatType type;
	private static NumberFormat numberFormat = NumberFormat.getIntegerInstance(Locale.US);
	public static IStatType simpleStatType = new StatTypeSimple();
	private static DecimalFormat decimalFormat = new DecimalFormat("########0.00");
	public static IStatType timeStatType = new StatTypeTime();
	public static IStatType distanceStatType = new StatTypeDistance();

	public StatBase(int var1, String var2, IStatType var3) {
		this.isIndependent = false;
		this.statId = var1;
		this.statName = var2;
		this.type = var3;
	}

	public StatBase(int var1, String var2) {
		this(var1, var2, simpleStatType);
	}

	public StatBase initIndependentStat() {
		this.isIndependent = true;
		return this;
	}

	public StatBase registerStat() {
		if(StatList.oneShotStats.containsKey(Integer.valueOf(this.statId))) {
			throw new RuntimeException("Duplicate stat id: \"" + ((StatBase)StatList.oneShotStats.get(Integer.valueOf(this.statId))).statName + "\" and \"" + this.statName + "\" at id " + this.statId);
		} else {
			StatList.field_25188_a.add(this);
			StatList.oneShotStats.put(Integer.valueOf(this.statId), this);
			this.statGuid = AchievementMap.getGuid(this.statId);
			return this;
		}
	}

	public boolean isAchievement() {
		return false;
	}

	public String func_27084_a(int var1) {
		return this.type.format(var1);
	}

	public String toString() {
		return this.statName;
	}

	static NumberFormat getNumberFormat() {
		return numberFormat;
	}

	static DecimalFormat getDecimalFormat() {
		return decimalFormat;
	}
}
