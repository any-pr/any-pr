package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet41EntityEffect extends Packet {
	public int field_35261_a;
	public byte field_35259_b;
	public byte field_35260_c;
	public short field_35258_d;

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35261_a = var1.readInt();
		this.field_35259_b = var1.readByte();
		this.field_35260_c = var1.readByte();
		this.field_35258_d = var1.readShort();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeInt(this.field_35261_a);
		var1.writeByte(this.field_35259_b);
		var1.writeByte(this.field_35260_c);
		var1.writeShort(this.field_35258_d);
	}

	public void processPacket(NetHandler var1) {
		var1.func_35780_a(this);
	}

	public int getPacketSize() {
		return 8;
	}
}
