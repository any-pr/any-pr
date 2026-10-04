package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet42RemoveEntityEffect extends Packet {
	public int field_35128_a;
	public byte field_35127_b;

	public Packet42RemoveEntityEffect() {
	}

	public Packet42RemoveEntityEffect(int var1, PotionEffect var2) {
		this.field_35128_a = var1;
		this.field_35127_b = (byte)(var2.func_35649_a() & 255);
	}

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35128_a = var1.readInt();
		this.field_35127_b = var1.readByte();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeInt(this.field_35128_a);
		var1.writeByte(this.field_35127_b);
	}

	public void processPacket(NetHandler var1) {
		var1.func_35003_a(this);
	}

	public int getPacketSize() {
		return 5;
	}
}
