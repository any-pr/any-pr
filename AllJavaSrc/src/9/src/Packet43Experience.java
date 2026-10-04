package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet43Experience extends Packet {
	public int field_35230_a;
	public int field_35228_b;
	public int field_35229_c;

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35230_a = var1.readByte();
		this.field_35229_c = var1.readByte();
		this.field_35228_b = var1.readShort();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeByte(this.field_35230_a);
		var1.writeByte(this.field_35229_c);
		var1.writeShort(this.field_35228_b);
	}

	public void processPacket(NetHandler var1) {
		var1.func_35777_a(this);
	}

	public int getPacketSize() {
		return 4;
	}
}
