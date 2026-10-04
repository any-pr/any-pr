package net.minecraft.src;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

public class StatsSyncher {
	private volatile boolean isBusy = false;
	private volatile Map field_27437_b = null;
	private volatile Map field_27436_c = null;
	private StatFileWriter statFileWriter;
	private File unsentDataFile;
	private File dataFile;
	private File unsentTempFile;
	private File tempFile;
	private File unsentOldFile;
	private File oldFile;
	private Session theSession;
	private int field_27427_l = 0;
	private int field_27426_m = 0;

	public StatsSyncher(Session var1, StatFileWriter var2, File var3) {
		this.unsentDataFile = new File(var3, "stats_" + var1.username.toLowerCase() + "_unsent.dat");
		this.dataFile = new File(var3, "stats_" + var1.username.toLowerCase() + ".dat");
		this.unsentOldFile = new File(var3, "stats_" + var1.username.toLowerCase() + "_unsent.old");
		this.oldFile = new File(var3, "stats_" + var1.username.toLowerCase() + ".old");
		this.unsentTempFile = new File(var3, "stats_" + var1.username.toLowerCase() + "_unsent.tmp");
		this.tempFile = new File(var3, "stats_" + var1.username.toLowerCase() + ".tmp");
		if(!var1.username.toLowerCase().equals(var1.username)) {
			this.func_28214_a(var3, "stats_" + var1.username + "_unsent.dat", this.unsentDataFile);
			this.func_28214_a(var3, "stats_" + var1.username + ".dat", this.dataFile);
			this.func_28214_a(var3, "stats_" + var1.username + "_unsent.old", this.unsentOldFile);
			this.func_28214_a(var3, "stats_" + var1.username + ".old", this.oldFile);
			this.func_28214_a(var3, "stats_" + var1.username + "_unsent.tmp", this.unsentTempFile);
			this.func_28214_a(var3, "stats_" + var1.username + ".tmp", this.tempFile);
		}

		this.statFileWriter = var2;
		this.theSession = var1;
		if(this.unsentDataFile.exists()) {
			var2.func_27179_a(this.func_27415_a(this.unsentDataFile, this.unsentTempFile, this.unsentOldFile));
		}

		this.beginReceiveStats();
	}

	private void func_28214_a(File var1, String var2, File var3) {
		File var4 = new File(var1, var2);
		if(var4.exists() && !var4.isDirectory() && !var3.exists()) {
			var4.renameTo(var3);
		}

	}

	private Map func_27415_a(File var1, File var2, File var3) {
		return var1.exists() ? this.func_27408_a(var1) : (var3.exists() ? this.func_27408_a(var3) : (var2.exists() ? this.func_27408_a(var2) : null));
	}

	private Map func_27408_a(File var1) {
		BufferedReader var2 = null;

		try {
			var2 = new BufferedReader(new FileReader(var1));
			String var3 = "";
			StringBuilder var4 = new StringBuilder();

			while(true) {
				var3 = var2.readLine();
				if(var3 == null) {
					Map var5 = StatFileWriter.func_27177_a(var4.toString());
					return var5;
				}

				var4.append(var3);
			}
		} catch (Exception var15) {
			var15.printStackTrace();
		} finally {
			if(var2 != null) {
				try {
					var2.close();
				} catch (Exception var14) {
					var14.printStackTrace();
				}
			}

		}

		return null;
	}

	private void func_27410_a(Map var1, File var2, File var3, File var4) throws IOException {
		PrintWriter var5 = new PrintWriter(new FileWriter(var3, false));

		try {
			var5.print(StatFileWriter.func_27185_a(this.theSession.username, "local", var1));
		} finally {
			var5.close();
		}

		if(var4.exists()) {
			var4.delete();
		}

		if(var2.exists()) {
			var2.renameTo(var4);
		}

		var3.renameTo(var2);
	}

	public void beginReceiveStats() {
		if(this.isBusy) {
			throw new IllegalStateException("Can\'t get stats from server while StatsSyncher is busy!");
		} else {
			this.field_27427_l = 100;
			this.isBusy = true;
			(new ThreadStatSyncherReceive(this)).start();
		}
	}

	public void beginSendStats(Map var1) {
		if(this.isBusy) {
			throw new IllegalStateException("Can\'t save stats while StatsSyncher is busy!");
		} else {
			this.field_27427_l = 100;
			this.isBusy = true;
			(new ThreadStatSyncherSend(this, var1)).start();
		}
	}

	public void syncStatsFileWithMap(Map var1) {
		int var2 = 30;

		while(this.isBusy) {
			--var2;
			if(var2 <= 0) {
				break;
			}

			try {
				Thread.sleep(100L);
			} catch (InterruptedException var10) {
				var10.printStackTrace();
			}
		}

		this.isBusy = true;

		try {
			this.func_27410_a(var1, this.unsentDataFile, this.unsentTempFile, this.unsentOldFile);
		} catch (Exception var8) {
			var8.printStackTrace();
		} finally {
			this.isBusy = false;
		}

	}

	public boolean func_27420_b() {
		return this.field_27427_l <= 0 && !this.isBusy && this.field_27436_c == null;
	}

	public void func_27425_c() {
		if(this.field_27427_l > 0) {
			--this.field_27427_l;
		}

		if(this.field_27426_m > 0) {
			--this.field_27426_m;
		}

		if(this.field_27436_c != null) {
			this.statFileWriter.func_27187_c(this.field_27436_c);
			this.field_27436_c = null;
		}

		if(this.field_27437_b != null) {
			this.statFileWriter.func_27180_b(this.field_27437_b);
			this.field_27437_b = null;
		}

	}

	static Map func_27422_a(StatsSyncher var0) {
		return var0.field_27437_b;
	}

	static File func_27423_b(StatsSyncher var0) {
		return var0.dataFile;
	}

	static File func_27411_c(StatsSyncher var0) {
		return var0.tempFile;
	}

	static File func_27413_d(StatsSyncher var0) {
		return var0.oldFile;
	}

	static void func_27412_a(StatsSyncher var0, Map var1, File var2, File var3, File var4) throws IOException {
		var0.func_27410_a(var1, var2, var3, var4);
	}

	static Map func_27421_a(StatsSyncher var0, Map var1) {
		return var0.field_27437_b = var1;
	}

	static Map func_27409_a(StatsSyncher var0, File var1, File var2, File var3) {
		return var0.func_27415_a(var1, var2, var3);
	}

	static boolean setBusy(StatsSyncher var0, boolean var1) {
		return var0.isBusy = var1;
	}

	static File getUnsentDataFile(StatsSyncher var0) {
		return var0.unsentDataFile;
	}

	static File getUnsentTempFile(StatsSyncher var0) {
		return var0.unsentTempFile;
	}

	static File getUnsentOldFile(StatsSyncher var0) {
		return var0.unsentOldFile;
	}
}
