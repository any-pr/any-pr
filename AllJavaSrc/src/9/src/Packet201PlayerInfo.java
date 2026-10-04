package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet201PlayerInfo extends Packet {
	public String field_35257_a;
	public boolean field_35255_b;
	public int field_35256_c;

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35257_a = readString(var1, 16);
		this.field_35255_b = var1.readByte() != 0;
		this.field_35256_c = var1.readShort();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		writeString(this.field_35257_a, var1);
		var1.writeByte(this.field_35255_b ? 1 : 0);
		var1.writeShort(this.field_35256_c);
	}

	public void processPacket(NetHandler var1) {
		var1.func_35779_a(this);
	}

	public int getPacketSize() {
		return this.field_35257_a.length() + 2 + 1 + 2;
	}
}
