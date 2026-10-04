package net.minecraft.src;

import java.io.IOException;
import java.net.Socket;
import java.util.Iterator;
import java.util.Random;
import java.util.logging.Logger;
import net.minecraft.server.MinecraftServer;

public class NetLoginHandler extends NetHandler {
	public static Logger logger = Logger.getLogger("Minecraft");
	private static Random rand = new Random();
	public NetworkManager netManager;
	public boolean finishedProcessing = false;
	private MinecraftServer mcServer;
	private int loginTimer = 0;
	private String username = null;
	private Packet1Login packet1login = null;
	private String serverId = "";

	public NetLoginHandler(MinecraftServer var1, Socket var2, String var3) throws IOException {
		this.mcServer = var1;
		this.netManager = new NetworkManager(var2, var3, this);
		this.netManager.chunkDataSendCounter = 0;
	}

	public void tryLogin() {
		if(this.packet1login != null) {
			this.doLogin(this.packet1login);
			this.packet1login = null;
		}

		if(this.loginTimer++ == 600) {
			this.kickUser("Took too long to log in");
		} else {
			this.netManager.processReadPackets();
		}

	}

	public void kickUser(String var1) {
		try {
			logger.info("Disconnecting " + this.getUserAndIPString() + ": " + var1);
			this.netManager.addToSendQueue(new Packet255KickDisconnect(var1));
			this.netManager.serverShutdown();
			this.finishedProcessing = true;
		} catch (Exception var3) {
			var3.printStackTrace();
		}

	}

	public void handleHandshake(Packet2Handshake var1) {
		if(this.mcServer.onlineMode) {
			this.serverId = Long.toHexString(rand.nextLong());
			this.netManager.addToSendQueue(new Packet2Handshake(this.serverId));
		} else {
			this.netManager.addToSendQueue(new Packet2Handshake("-"));
		}

	}

	public void handleLogin(Packet1Login var1) {
		this.username = var1.username;
		if(var1.protocolVersion != 17) {
			if(var1.protocolVersion > 17) {
				this.kickUser("Outdated server!");
			} else {
				this.kickUser("Outdated client!");
			}

		} else {
			if(!this.mcServer.onlineMode) {
				this.doLogin(var1);
			} else {
				(new ThreadLoginVerifier(this, var1)).start();
			}

		}
	}

	public void doLogin(Packet1Login var1) {
		EntityPlayerMP var2 = this.mcServer.configManager.login(this, var1.username);
		if(var2 != null) {
			this.mcServer.configManager.readPlayerDataFromFile(var2);
			var2.Sets(this.mcServer.getWorldManager(var2.dimension));
			var2.itemInWorldManager.func_35694_a((WorldServer)var2.worldObj);
			logger.info(this.getUserAndIPString() + " logged in with entity id " + var2.entityId + " at (" + var2.posX + ", " + var2.posY + ", " + var2.posZ + ")");
			WorldServer var3 = this.mcServer.getWorldManager(var2.dimension);
			ChunkCoordinates var4 = var3.getSpawnPoint();
			var2.itemInWorldManager.func_35695_b(var3.getWorldInfo().func_35501_n());
			NetServerHandler var5 = new NetServerHandler(this.mcServer, this.netManager, var2);
			int var10004 = var2.entityId;
			long var10005 = var3.getRandomSeed();
			int var10006 = var2.itemInWorldManager.func_35697_a();
			byte var10007 = (byte)var3.worldProvider.worldType;
			byte var10008 = (byte)var3.difficultySetting;
			var3.getClass();
			var5.sendPacket(new Packet1Login("", var10004, var10005, var10006, var10007, var10008, (byte)-128, (byte)this.mcServer.configManager.func_35480_h()));
			var5.sendPacket(new Packet6SpawnPosition(var4.posX, var4.posY, var4.posZ));
			this.mcServer.configManager.func_28170_a(var2, var3);
			this.mcServer.configManager.sendPacketToAllPlayers(new Packet3Chat("\u00a7e" + var2.username + " joined the game."));
			this.mcServer.configManager.playerLoggedIn(var2);
			var5.teleportTo(var2.posX, var2.posY, var2.posZ, var2.rotationYaw, var2.rotationPitch);
			this.mcServer.networkServer.addPlayer(var5);
			var5.sendPacket(new Packet4UpdateTime(var3.getWorldTime()));
			Iterator var6 = var2.func_35183_ak().iterator();

			while(var6.hasNext()) {
				PotionEffect var7 = (PotionEffect)var6.next();
				var5.sendPacket(new Packet41EntityEffect(var2.entityId, var7));
			}

			var2.func_20057_k();
		}

		this.finishedProcessing = true;
	}

	public void handleErrorMessage(String var1, Object[] var2) {
		logger.info(this.getUserAndIPString() + " lost connection");
		this.finishedProcessing = true;
	}

	public void func_35007_a(Packet254ServerPing var1) {
		try {
			String var2 = this.mcServer.field_35014_p + "\u00a7" + this.mcServer.configManager.func_35481_g() + "\u00a7" + this.mcServer.configManager.func_35480_h();
			this.netManager.addToSendQueue(new Packet255KickDisconnect(var2));
			this.netManager.serverShutdown();
			this.mcServer.networkServer.func_35505_a(this.netManager.func_35596_f());
			this.finishedProcessing = true;
		} catch (Exception var3) {
			var3.printStackTrace();
		}

	}

	public void registerPacket(Packet var1) {
		this.kickUser("Protocol error");
	}

	public String getUserAndIPString() {
		return this.username != null ? this.username + " [" + this.netManager.getRemoteAddress().toString() + "]" : this.netManager.getRemoteAddress().toString();
	}

	public boolean isServerHandler() {
		return true;
	}

	static String getServerId(NetLoginHandler var0) {
		return var0.serverId;
	}

	static Packet1Login setLoginPacket(NetLoginHandler var0, Packet1Login var1) {
		return var0.packet1login = var1;
	}
}
