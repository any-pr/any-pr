package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet107CreativeSetSlot extends Packet {
	public int field_35236_a;
	public int field_35234_b;
	public int field_35235_c;
	public int field_35233_d;

	public Packet107CreativeSetSlot() {
	}

	public Packet107CreativeSetSlot(int var1, int var2, int var3, int var4) {
		this.field_35236_a = var1;
		this.field_35234_b = var2;
		this.field_35235_c = var3;
		this.field_35233_d = var4;
	}

	public void processPacket(NetHandler var1) {
		var1.func_35781_a(this);
	}

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35236_a = var1.readShort();
		this.field_35234_b = var1.readShort();
		this.field_35235_c = var1.readShort();
		this.field_35233_d = var1.readShort();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeShort(this.field_35236_a);
		var1.writeShort(this.field_35234_b);
		var1.writeShort(this.field_35235_c);
		var1.writeShort(this.field_35233_d);
	}

	public int getPacketSize() {
		return 8;
	}
}
