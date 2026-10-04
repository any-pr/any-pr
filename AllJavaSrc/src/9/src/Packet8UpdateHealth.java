package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet8UpdateHealth extends Packet {
	public int healthMP;
	public int field_35231_b;
	public float field_35232_c;

	public void readPacketData(DataInputStream var1) throws IOException {
		this.healthMP = var1.readShort();
		this.field_35231_b = var1.readShort();
		this.field_35232_c = var1.readFloat();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeShort(this.healthMP);
		var1.writeShort(this.field_35231_b);
		var1.writeFloat(this.field_35232_c);
	}

	public void processPacket(NetHandler var1) {
		var1.handleHealth(this);
	}

	public int getPacketSize() {
		return 8;
	}
}
