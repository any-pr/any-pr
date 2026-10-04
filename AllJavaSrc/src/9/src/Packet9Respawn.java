package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet9Respawn extends Packet {
	public long field_35246_a;
	public int field_35244_b;
	public int field_35245_c;
	public int field_35242_d;
	public int field_35243_e;

	public Packet9Respawn() {
	}

	public Packet9Respawn(byte var1, byte var2, long var3, int var5, int var6) {
		this.field_35244_b = var1;
		this.field_35245_c = var2;
		this.field_35246_a = var3;
		this.field_35242_d = var5;
		this.field_35243_e = var6;
	}

	public void processPacket(NetHandler var1) {
		var1.handleRespawn(this);
	}

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35244_b = var1.readByte();
		this.field_35245_c = var1.readByte();
		this.field_35243_e = var1.readByte();
		this.field_35242_d = var1.readShort();
		this.field_35246_a = var1.readLong();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeByte(this.field_35244_b);
		var1.writeByte(this.field_35245_c);
		var1.writeByte(this.field_35243_e);
		var1.writeShort(this.field_35242_d);
		var1.writeLong(this.field_35246_a);
	}

	public int getPacketSize() {
		return 13;
	}
}
