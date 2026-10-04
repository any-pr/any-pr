package net.minecraft.src;

import java.io.ByteArrayOutputStream;

class RegionFileChunkBuffer extends ByteArrayOutputStream {
	private int chunkX;
	private int chunkZ;
	final RegionFile regionFile;

	public RegionFileChunkBuffer(RegionFile var1, int var2, int var3) {
		super(8096);
		this.regionFile = var1;
		this.chunkX = var2;
		this.chunkZ = var3;
	}

	public void close() {
		this.regionFile.write(this.chunkX, this.chunkZ, this.buf, this.count);
	}
}
