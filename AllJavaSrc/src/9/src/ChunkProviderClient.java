package net.minecraft.src;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChunkProviderClient implements IChunkProvider {
	private Chunk blankChunk;
	private PlayerList chunkMapping = new PlayerList();
	private List field_889_c = new ArrayList();
	private World worldObj;

	public ChunkProviderClient(World var1) {
		var1.getClass();
		this.blankChunk = new EmptyChunk(var1, new byte[256 * 128], 0, 0);
		this.worldObj = var1;
	}

	public boolean chunkExists(int var1, int var2) {
		return this != null ? true : this.chunkMapping.func_35575_b(ChunkCoordIntPair.chunkXZ2Int(var1, var2));
	}

	public void func_539_c(int var1, int var2) {
		Chunk var3 = this.provideChunk(var1, var2);
		if(!var3.func_21167_h()) {
			var3.onChunkUnload();
		}

		this.chunkMapping.func_35574_d(ChunkCoordIntPair.chunkXZ2Int(var1, var2));
		this.field_889_c.remove(var3);
	}

	public Chunk loadChunk(int var1, int var2) {
		this.worldObj.getClass();
		byte[] var3 = new byte[256 * 128];
		Chunk var4 = new Chunk(this.worldObj, var3, var1, var2);
		Arrays.fill(var4.skylightMap.data, (byte)-1);
		this.chunkMapping.func_35577_a(ChunkCoordIntPair.chunkXZ2Int(var1, var2), var4);
		var4.isChunkLoaded = true;
		return var4;
	}

	public Chunk provideChunk(int var1, int var2) {
		Chunk var3 = (Chunk)this.chunkMapping.func_35578_a(ChunkCoordIntPair.chunkXZ2Int(var1, var2));
		return var3 == null ? this.blankChunk : var3;
	}

	public boolean saveChunks(boolean var1, IProgressUpdate var2) {
		return true;
	}

	public boolean unload100OldestChunks() {
		return false;
	}

	public boolean canSave() {
		return false;
	}

	public void populate(IChunkProvider var1, int var2, int var3) {
	}

	public String makeString() {
		return "MultiplayerChunkCache: " + this.chunkMapping.func_35576_a();
	}
}
