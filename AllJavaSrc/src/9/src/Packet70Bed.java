package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet70Bed extends Packet {
	public static final String[] bedChat = new String[]{"tile.bed.notValid", null, null, "gameMode.changed"};
	public int bedState;
	public int field_35262_c;

	public void readPacketData(DataInputStream var1) throws IOException {
		this.bedState = var1.readByte();
		this.field_35262_c = var1.readByte();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeByte(this.bedState);
		var1.writeByte(this.field_35262_c);
	}

	public void processPacket(NetHandler var1) {
		var1.handleBedUpdate(this);
	}

	public int getPacketSize() {
		return 2;
	}
}
