package net.minecraft.src;

public class MovementInputFromOptions extends MovementInput {
	private GameSettings gameSettings;

	public MovementInputFromOptions(GameSettings var1) {
		this.gameSettings = var1;
	}

	public void updatePlayerMoveState(EntityPlayer var1) {
		this.moveStrafe = 0.0F;
		this.moveForward = 0.0F;
		if(this.gameSettings.keyBindForward.field_35965_e) {
			++this.moveForward;
		}

		if(this.gameSettings.keyBindBack.field_35965_e) {
			--this.moveForward;
		}

		if(this.gameSettings.keyBindLeft.field_35965_e) {
			++this.moveStrafe;
		}

		if(this.gameSettings.keyBindRight.field_35965_e) {
			--this.moveStrafe;
		}

		this.jump = this.gameSettings.keyBindJump.field_35965_e;
		this.sneak = this.gameSettings.keyBindSneak.field_35965_e;
		if(this.sneak) {
			this.moveStrafe = (float)((double)this.moveStrafe * 0.3D);
			this.moveForward = (float)((double)this.moveForward * 0.3D);
		}

	}
}
