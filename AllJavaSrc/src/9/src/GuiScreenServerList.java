package net.minecraft.src;

import org.lwjgl.input.Keyboard;

public class GuiScreenServerList extends GuiScreen {
	private GuiScreen field_35319_a;
	private GuiTextField field_35317_b;
	private ServerNBTStorage field_35318_c;

	public GuiScreenServerList(GuiScreen var1, ServerNBTStorage var2) {
		this.field_35319_a = var1;
		this.field_35318_c = var2;
	}

	public void updateScreen() {
		this.field_35317_b.updateCursorCounter();
	}

	public void initGui() {
		StringTranslate var1 = StringTranslate.getInstance();
		Keyboard.enableRepeatEvents(true);
		this.controlList.clear();
		this.controlList.add(new GuiButton(0, this.width / 2 - 100, this.height / 4 + 96 + 12, var1.translateKey("selectServer.select")));
		this.controlList.add(new GuiButton(1, this.width / 2 - 100, this.height / 4 + 120 + 12, var1.translateKey("gui.cancel")));
		this.field_35317_b = new GuiTextField(this, this.fontRenderer, this.width / 2 - 100, 116, 200, 20, this.field_35318_c.field_35793_b);
		this.field_35317_b.setMaxStringLength(128);
		((GuiButton)this.controlList.get(0)).enabled = this.field_35317_b.getText().length() > 0;
	}

	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	protected void actionPerformed(GuiButton var1) {
		if(var1.enabled) {
			if(var1.id == 1) {
				this.field_35319_a.deleteWorld(false, 0);
			} else if(var1.id == 0) {
				this.field_35318_c.field_35793_b = this.field_35317_b.getText();
				this.field_35319_a.deleteWorld(true, 0);
			}

		}
	}

	protected void keyTyped(char var1, int var2) {
		this.field_35317_b.textboxKeyTyped(var1, var2);
		if(var1 == 13) {
			this.actionPerformed((GuiButton)this.controlList.get(0));
		}

		((GuiButton)this.controlList.get(0)).enabled = this.field_35317_b.getText().length() > 0;
	}

	protected void mouseClicked(int var1, int var2, int var3) {
		super.mouseClicked(var1, var2, var3);
		this.field_35317_b.mouseClicked(var1, var2, var3);
	}

	public void drawScreen(int var1, int var2, float var3) {
		StringTranslate var4 = StringTranslate.getInstance();
		this.drawDefaultBackground();
		this.drawCenteredString(this.fontRenderer, var4.translateKey("selectServer.direct"), this.width / 2, this.height / 4 - 60 + 20, 16777215);
		this.drawString(this.fontRenderer, var4.translateKey("addServer.enterIp"), this.width / 2 - 100, 100, 10526880);
		this.field_35317_b.drawTextBox();
		super.drawScreen(var1, var2, var3);
	}
}
