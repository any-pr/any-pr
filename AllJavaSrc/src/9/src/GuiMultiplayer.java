package net.minecraft.src;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Keyboard;

public class GuiMultiplayer extends GuiScreen {
	private static int field_35344_a = 0;
	private static Object field_35343_b = new Object();
	private GuiScreen parentScreen;
	private GuiSlotServer field_35342_d;
	private List field_35340_f = new ArrayList();
	private int field_35341_g = -1;
	private GuiButton field_35347_h;
	private GuiButton field_35348_i;
	private GuiButton field_35345_j;
	private boolean field_35346_k = false;
	private boolean field_35353_s = false;
	private boolean field_35352_t = false;
	private boolean field_35351_u = false;
	private String field_35350_v = null;
	private ServerNBTStorage field_35349_w = null;

	public GuiMultiplayer(GuiScreen var1) {
		this.parentScreen = var1;
	}

	public void updateScreen() {
	}

	public void initGui() {
		this.func_35324_p();
		Keyboard.enableRepeatEvents(true);
		this.controlList.clear();
		this.field_35342_d = new GuiSlotServer(this);
		this.func_35337_c();
	}

	private void func_35324_p() {
		try {
			NBTTagCompound var1 = CompressedStreamTools.func_35622_a(new File(this.mc.mcDataDir, "servers.dat"));
			NBTTagList var2 = var1.getTagList("servers");
			this.field_35340_f.clear();

			for(int var3 = 0; var3 < var2.tagCount(); ++var3) {
				this.field_35340_f.add(ServerNBTStorage.func_35788_a((NBTTagCompound)var2.tagAt(var3)));
			}
		} catch (Exception var4) {
			var4.printStackTrace();
		}

	}

	private void func_35323_q() {
		try {
			NBTTagList var1 = new NBTTagList();

			for(int var2 = 0; var2 < this.field_35340_f.size(); ++var2) {
				var1.setTag(((ServerNBTStorage)this.field_35340_f.get(var2)).func_35789_a());
			}

			NBTTagCompound var4 = new NBTTagCompound();
			var4.setTag("servers", var1);
			CompressedStreamTools.func_35621_a(var4, new File(this.mc.mcDataDir, "servers.dat"));
		} catch (Exception var3) {
			var3.printStackTrace();
		}

	}

	public void func_35337_c() {
		StringTranslate var1 = StringTranslate.getInstance();
		this.controlList.add(this.field_35347_h = new GuiButton(7, this.width / 2 - 154, this.height - 28, 70, 20, var1.translateKey("selectServer.edit")));
		this.controlList.add(this.field_35345_j = new GuiButton(2, this.width / 2 - 74, this.height - 28, 70, 20, var1.translateKey("selectServer.delete")));
		this.controlList.add(this.field_35348_i = new GuiButton(1, this.width / 2 - 154, this.height - 52, 100, 20, var1.translateKey("selectServer.select")));
		this.controlList.add(new GuiButton(4, this.width / 2 - 50, this.height - 52, 100, 20, var1.translateKey("selectServer.direct")));
		this.controlList.add(new GuiButton(3, this.width / 2 + 4 + 50, this.height - 52, 100, 20, var1.translateKey("selectServer.add")));
		this.controlList.add(new GuiButton(8, this.width / 2 + 4, this.height - 28, 70, 20, var1.translateKey("selectServer.refresh")));
		this.controlList.add(new GuiButton(0, this.width / 2 + 4 + 76, this.height - 28, 75, 20, var1.translateKey("gui.cancel")));
		boolean var2 = this.field_35341_g >= 0 && this.field_35341_g < this.field_35342_d.getSize();
		this.field_35348_i.enabled = var2;
		this.field_35347_h.enabled = var2;
		this.field_35345_j.enabled = var2;
	}

	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
	}

	protected void actionPerformed(GuiButton var1) {
		if(var1.enabled) {
			if(var1.id == 2) {
				String var2 = ((ServerNBTStorage)this.field_35340_f.get(this.field_35341_g)).field_35795_a;
				if(var2 != null) {
					this.field_35346_k = true;
					StringTranslate var3 = StringTranslate.getInstance();
					String var4 = var3.translateKey("selectServer.deleteQuestion");
					String var5 = "\'" + var2 + "\' " + var3.translateKey("selectServer.deleteWarning");
					String var6 = var3.translateKey("selectServer.deleteButton");
					String var7 = var3.translateKey("gui.cancel");
					GuiYesNo var8 = new GuiYesNo(this, var4, var5, var6, var7, this.field_35341_g);
					this.mc.displayGuiScreen(var8);
				}
			} else if(var1.id == 1) {
				this.func_35322_a(this.field_35341_g);
			} else if(var1.id == 4) {
				this.field_35351_u = true;
				this.mc.displayGuiScreen(new GuiScreenServerList(this, this.field_35349_w = new ServerNBTStorage(StatCollector.translateToLocal("selectServer.defaultName"), "")));
			} else if(var1.id == 3) {
				this.field_35353_s = true;
				this.mc.displayGuiScreen(new GuiScreenAddServer(this, this.field_35349_w = new ServerNBTStorage(StatCollector.translateToLocal("selectServer.defaultName"), "")));
			} else if(var1.id == 7) {
				this.field_35352_t = true;
				ServerNBTStorage var9 = (ServerNBTStorage)this.field_35340_f.get(this.field_35341_g);
				this.mc.displayGuiScreen(new GuiScreenAddServer(this, this.field_35349_w = new ServerNBTStorage(var9.field_35795_a, var9.field_35793_b)));
			} else if(var1.id == 0) {
				this.mc.displayGuiScreen(this.parentScreen);
			} else if(var1.id == 8) {
				this.mc.displayGuiScreen(new GuiMultiplayer(this.parentScreen));
			} else {
				this.field_35342_d.actionPerformed(var1);
			}

		}
	}

	public void deleteWorld(boolean var1, int var2) {
		if(this.field_35346_k) {
			this.field_35346_k = false;
			if(var1) {
				this.field_35340_f.remove(var2);
				this.func_35323_q();
			}

			this.mc.displayGuiScreen(this);
		} else if(this.field_35351_u) {
			this.field_35351_u = false;
			if(var1) {
				this.func_35330_a(this.field_35349_w);
			} else {
				this.mc.displayGuiScreen(this);
			}
		} else if(this.field_35353_s) {
			this.field_35353_s = false;
			if(var1) {
				this.field_35340_f.add(this.field_35349_w);
				this.func_35323_q();
			}

			this.mc.displayGuiScreen(this);
		} else if(this.field_35352_t) {
			this.field_35352_t = false;
			if(var1) {
				ServerNBTStorage var3 = (ServerNBTStorage)this.field_35340_f.get(this.field_35341_g);
				var3.field_35795_a = this.field_35349_w.field_35795_a;
				var3.field_35793_b = this.field_35349_w.field_35793_b;
				this.func_35323_q();
			}

			this.mc.displayGuiScreen(this);
		}

	}

	private int parseIntWithDefault(String var1, int var2) {
		try {
			return Integer.parseInt(var1.trim());
		} catch (Exception var4) {
			return var2;
		}
	}

	protected void keyTyped(char var1, int var2) {
		if(var1 == 13) {
			this.actionPerformed((GuiButton)this.controlList.get(2));
		}

	}

	protected void mouseClicked(int var1, int var2, int var3) {
		super.mouseClicked(var1, var2, var3);
	}

	public void drawScreen(int var1, int var2, float var3) {
		this.field_35350_v = null;
		StringTranslate var4 = StringTranslate.getInstance();
		this.drawDefaultBackground();
		this.field_35342_d.drawScreen(var1, var2, var3);
		this.drawCenteredString(this.fontRenderer, var4.translateKey("multiplayer.title"), this.width / 2, 20, 16777215);
		super.drawScreen(var1, var2, var3);
		if(this.field_35350_v != null) {
			this.func_35325_a(this.field_35350_v, var1, var2);
		}

	}

	private void func_35322_a(int var1) {
		this.func_35330_a((ServerNBTStorage)this.field_35340_f.get(var1));
	}

	private void func_35330_a(ServerNBTStorage var1) {
		String var2 = var1.field_35793_b;
		String[] var3 = var2.split(":");
		if(var2.startsWith("[")) {
			int var4 = var2.indexOf("]");
			if(var4 > 0) {
				String var5 = var2.substring(1, var4);
				String var6 = var2.substring(var4 + 1).trim();
				if(var6.startsWith(":") && var6.length() > 0) {
					var6 = var6.substring(1);
					var3 = new String[]{var5, var6};
				} else {
					var3 = new String[]{var5};
				}
			}
		}

		if(var3.length > 2) {
			var3 = new String[]{var2};
		}

		this.mc.displayGuiScreen(new GuiConnecting(this.mc, var3[0], var3.length > 1 ? this.parseIntWithDefault(var3[1], 25565) : 25565));
	}

	private void func_35328_b(ServerNBTStorage var1) throws IOException {
		String var2 = var1.field_35793_b;
		String[] var3 = var2.split(":");
		if(var2.startsWith("[")) {
			int var4 = var2.indexOf("]");
			if(var4 > 0) {
				String var5 = var2.substring(1, var4);
				String var6 = var2.substring(var4 + 1).trim();
				if(var6.startsWith(":") && var6.length() > 0) {
					var6 = var6.substring(1);
					var3 = new String[]{var5, var6};
				} else {
					var3 = new String[]{var5};
				}
			}
		}

		if(var3.length > 2) {
			var3 = new String[]{var2};
		}

		String var29 = var3[0];
		int var30 = var3.length > 1 ? this.parseIntWithDefault(var3[1], 25565) : 25565;
		Socket var31 = null;
		DataInputStream var7 = null;
		DataOutputStream var8 = null;

		try {
			var31 = new Socket();
			var31.setSoTimeout(3000);
			var31.setTcpNoDelay(true);
			var31.setTrafficClass(18);
			var31.connect(new InetSocketAddress(var29, var30), 3000);
			var7 = new DataInputStream(var31.getInputStream());
			var8 = new DataOutputStream(var31.getOutputStream());
			var8.write(254);
			if(var7.read() != 255) {
				throw new IOException("Bad message");
			}

			String var9 = Packet.readString(var7, 64);
			char[] var10 = var9.toCharArray();

			int var11;
			for(var11 = 0; var11 < var10.length; ++var11) {
				if(var10[var11] != 167 && ChatAllowedCharacters.allowedCharacters.indexOf(var10[var11]) < 0) {
					var10[var11] = 63;
				}
			}

			var9 = new String(var10);
			var3 = var9.split("\u00a7");
			var9 = var3[0];
			var11 = -1;
			int var12 = -1;

			try {
				var11 = Integer.parseInt(var3[1]);
				var12 = Integer.parseInt(var3[2]);
			} catch (Exception var27) {
			}

			var1.field_35791_d = "\u00a77" + var9;
			if(var11 >= 0 && var12 > 0) {
				var1.field_35794_c = "\u00a77" + var11 + "\u00a78/\u00a77" + var12;
			} else {
				var1.field_35794_c = "\u00a78???";
			}
		} finally {
			try {
				if(var7 != null) {
					var7.close();
				}
			} catch (Throwable var26) {
			}

			try {
				if(var8 != null) {
					var8.close();
				}
			} catch (Throwable var25) {
			}

			try {
				if(var31 != null) {
					var31.close();
				}
			} catch (Throwable var24) {
			}

		}

	}

	protected void func_35325_a(String var1, int var2, int var3) {
		if(var1 != null) {
			int var4 = var2 + 12;
			int var5 = var3 - 12;
			int var6 = this.fontRenderer.getStringWidth(var1);
			this.drawGradientRect(var4 - 3, var5 - 3, var4 + var6 + 3, var5 + 8 + 3, -1073741824, -1073741824);
			this.fontRenderer.drawStringWithShadow(var1, var4, var5, -1);
		}
	}

	static List func_35320_a(GuiMultiplayer var0) {
		return var0.field_35340_f;
	}

	static int func_35326_a(GuiMultiplayer var0, int var1) {
		return var0.field_35341_g = var1;
	}

	static int func_35333_b(GuiMultiplayer var0) {
		return var0.field_35341_g;
	}

	static GuiButton func_35329_c(GuiMultiplayer var0) {
		return var0.field_35348_i;
	}

	static GuiButton func_35334_d(GuiMultiplayer var0) {
		return var0.field_35347_h;
	}

	static GuiButton func_35339_e(GuiMultiplayer var0) {
		return var0.field_35345_j;
	}

	static void func_35332_b(GuiMultiplayer var0, int var1) {
		var0.func_35322_a(var1);
	}

	static Object func_35321_g() {
		return field_35343_b;
	}

	static int func_35338_m() {
		return field_35344_a;
	}

	static int func_35331_n() {
		return field_35344_a++;
	}

	static void func_35336_a(GuiMultiplayer var0, ServerNBTStorage var1) throws IOException {
		var0.func_35328_b(var1);
	}

	static int func_35335_o() {
		return field_35344_a--;
	}

	static String func_35327_a(GuiMultiplayer var0, String var1) {
		return var0.field_35350_v = var1;
	}
}
