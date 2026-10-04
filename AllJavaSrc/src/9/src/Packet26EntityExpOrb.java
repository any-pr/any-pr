package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet26EntityExpOrb extends Packet {
	public int field_35241_a;
	public int field_35239_b;
	public int field_35240_c;
	public int field_35237_d;
	public int field_35238_e;

	public Packet26EntityExpOrb() {
	}

	public Packet26EntityExpOrb(EntityXPOrb var1) {
		this.field_35241_a = var1.entityId;
		this.field_35239_b = MathHelper.floor_double(var1.posX * 32.0D);
		this.field_35240_c = MathHelper.floor_double(var1.posY * 32.0D);
		this.field_35237_d = MathHelper.floor_double(var1.posZ * 32.0D);
		this.field_35238_e = var1.func_35119_j_();
	}

	public void readPacketData(DataInputStream var1) throws IOException {
		this.field_35241_a = var1.readInt();
		this.field_35239_b = var1.readInt();
		this.field_35240_c = var1.readInt();
		this.field_35237_d = var1.readInt();
		this.field_35238_e = var1.readShort();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeInt(this.field_35241_a);
		var1.writeInt(this.field_35239_b);
		var1.writeInt(this.field_35240_c);
		var1.writeInt(this.field_35237_d);
		var1.writeShort(this.field_35238_e);
	}

	public void processPacket(NetHandler var1) {
		var1.func_35778_a(this);
	}

	public int getPacketSize() {
		return 18;
	}
}
