package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet27Position extends Packet {
	private float strafeMovement;
	private float fowardMovement;
	private boolean field_22043_c;
	private boolean isInJump;
	private float pitchRotation;
	private float yawRotation;

	public void readPacketData(DataInputStream var1) throws IOException {
		this.strafeMovement = var1.readFloat();
		this.fowardMovement = var1.readFloat();
		this.pitchRotation = var1.readFloat();
		this.yawRotation = var1.readFloat();
		this.field_22043_c = var1.readBoolean();
		this.isInJump = var1.readBoolean();
	}

	public void writePacketData(DataOutputStream var1) throws IOException {
		var1.writeFloat(this.strafeMovement);
		var1.writeFloat(this.fowardMovement);
		var1.writeFloat(this.pitchRotation);
		var1.writeFloat(this.yawRotation);
		var1.writeBoolean(this.field_22043_c);
		var1.writeBoolean(this.isInJump);
	}

	public void processPacket(NetHandler var1) {
		var1.handlePosition(this);
	}

	public int getPacketSize() {
		return 18;
	}
}
