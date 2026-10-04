package net.minecraft.src;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChunkProvider implements IChunkProvider {
	private Set droppedChunksSet = new HashSet();
	private Chunk field_28061_b;
	private IChunkProvider chunkProvider;
	private IChunkLoader chunkLoader;
	private PlayerHash chunkMap = new PlayerHash();
	private List chunkList = new ArrayList();
	private World worldObj;
	private int field_35557_h;

	public ChunkProvider(World var1, IChunkLoader var2, IChunkProvider var3) {
		var1.getClass();
		this.field_28061_b = new EmptyChunk(var1, new byte[256 * 128], 0, 0);
		this.worldObj = var1;
		this.chunkLoader = var2;
		this.chunkProvider = var3;
	}

	public boolean chunkExists(int var1, int var2) {
		return this.chunkMap.func_35508_b(ChunkCoordIntPair.chunkXZ2Int(var1, var2));
	}

	public void func_35556_d(int var1, int var2) {
		ChunkCoordinates var3 = this.worldObj.getSpawnPoint();
		int var4 = var1 * 16 + 8 - var3.posX;
		int var5 = var2 * 16 + 8 - var3.posZ;
		short var6 = 128;
		if(var4 < -var6 || var4 > var6 || var5 < -var6 || var5 > var6) {
			this.droppedChunksSet.add(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(var1, var2)));
		}

	}

	public Chunk loadChunk(int var1, int var2) {
		long var3 = ChunkCoordIntPair.chunkXZ2Int(var1, var2);
		this.droppedChunksSet.remove(Long.valueOf(var3));
		Chunk var5 = (Chunk)this.chunkMap.getValueByKey(var3);
		if(var5 == null) {
			int var6 = 1875004;
			if(var1 < -var6 || var2 < -var6 || var1 >= var6 || var2 >= var6) {
				return this.field_28061_b;
			}

			var5 = this.loadChunkFromFile(var1, var2);
			if(var5 == null) {
				if(this.chunkProvider == null) {
					var5 = this.field_28061_b;
				} else {
					var5 = this.chunkProvider.provideChunk(var1, var2);
				}
			}

			this.chunkMap.add(var3, var5);
			this.chunkList.add(var5);
			if(var5 != null) {
				var5.func_4053_c();
				var5.onChunkLoad();
			}

			var5.func_35632_a(this, this, var1, var2);
		}

		return var5;
	}

	public Chunk provideChunk(int var1, int var2) {
		Chunk var3 = (Chunk)this.chunkMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(var1, var2));
		return var3 == null ? this.loadChunk(var1, var2) : var3;
	}

	private Chunk loadChunkFromFile(int var1, int var2) {
		if(this.chunkLoader == null) {
			return null;
		} else {
			try {
				Chunk var3 = this.chunkLoader.loadChunk(this.worldObj, var1, var2);
				if(var3 != null) {
					var3.lastSaveTime = this.worldObj.getWorldTime();
				}

				return var3;
			} catch (Exception var4) {
				var4.printStackTrace();
				return null;
			}
		}
	}

	private void func_28060_a(Chunk var1) {
		if(this.chunkLoader != null) {
			try {
				this.chunkLoader.saveExtraChunkData(this.worldObj, var1);
			} catch (Exception var3) {
				var3.printStackTrace();
			}

		}
	}

	private void func_28059_b(Chunk var1) {
		if(this.chunkLoader != null) {
			try {
				var1.lastSaveTime = this.worldObj.getWorldTime();
				this.chunkLoader.saveChunk(this.worldObj, var1);
			} catch (IOException var3) {
				var3.printStackTrace();
			}

		}
	}

	public void populate(IChunkProvider var1, int var2, int var3) {
		Chunk var4 = this.provideChunk(var2, var3);
		if(!var4.isTerrainPopulated) {
			var4.isTerrainPopulated = true;
			if(this.chunkProvider != null) {
				this.chunkProvider.populate(var1, var2, var3);
				var4.setChunkModified();
			}
		}

	}

	public boolean saveChunks(boolean var1, IProgressUpdate var2) {
		int var3 = 0;

		for(int var4 = 0; var4 < this.chunkList.size(); ++var4) {
			Chunk var5 = (Chunk)this.chunkList.get(var4);
			if(var1 && !var5.neverSave) {
				this.func_28060_a(var5);
			}

			if(var5.needsSaving(var1)) {
				this.func_28059_b(var5);
				var5.isModified = false;
				++var3;
				if(var3 == 24 && !var1) {
					return false;
				}
			}
		}

		if(var1) {
			if(this.chunkLoader == null) {
				return true;
			}

			this.chunkLoader.saveExtraData();
		}

		return true;
	}

	public boolean unload100OldestChunks() {
		int var1;
		for(var1 = 0; var1 < 100; ++var1) {
			if(!this.droppedChunksSet.isEmpty()) {
				Long var2 = (Long)this.droppedChunksSet.iterator().next();
				Chunk var3 = (Chunk)this.chunkMap.getValueByKey(var2.longValue());
				var3.onChunkUnload();
				this.func_28059_b(var3);
				this.func_28060_a(var3);
				this.droppedChunksSet.remove(var2);
				this.chunkMap.remove(var2.longValue());
				this.chunkList.remove(var3);
			}
		}

		for(var1 = 0; var1 < 10; ++var1) {
			if(this.field_35557_h >= this.chunkList.size()) {
				this.field_35557_h = 0;
				break;
			}

			Chunk var4 = (Chunk)this.chunkList.get(this.field_35557_h++);
			EntityPlayer var5 = this.worldObj.getClosestPlayer((double)(var4.xPosition << 4) + 8.0D, 64.0D, (double)(var4.zPosition << 4) + 8.0D, 288.0D);
			if(var5 == null) {
				this.func_35556_d(var4.xPosition, var4.zPosition);
			}
		}

		if(this.chunkLoader != null) {
			this.chunkLoader.func_661_a();
		}

		return this.chunkProvider.unload100OldestChunks();
	}

	public boolean canSave() {
		return true;
	}
}
