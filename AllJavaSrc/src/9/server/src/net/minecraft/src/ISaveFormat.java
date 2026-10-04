package net.minecraft.src;

public interface ISaveFormat {
	boolean isOldSaveType(String var1);

	boolean convertMapFormat(String var1, IProgressUpdate var2);
}
