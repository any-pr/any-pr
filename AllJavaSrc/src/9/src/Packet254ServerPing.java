package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class Packet254ServerPing extends Packet {
	public void readPacketData(DataInputStream var1) {
	}

	public void writePacketData(DataOutputStream var1) {
	}

	public void processPacket(NetHandler var1) {
		var1.func_35782_a(this);
	}

	public int getPacketSize() {
		return 0;
	}
}
