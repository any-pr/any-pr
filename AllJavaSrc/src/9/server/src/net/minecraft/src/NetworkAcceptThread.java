package net.minecraft.src;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;
import net.minecraft.server.MinecraftServer;

class NetworkAcceptThread extends Thread {
	final MinecraftServer mcServer;
	final NetworkListenThread field_985_b;

	NetworkAcceptThread(NetworkListenThread var1, String var2, MinecraftServer var3) {
		super(var2);
		this.field_985_b = var1;
		this.mcServer = var3;
	}

	public void run() {
		while(this.field_985_b.field_973_b) {
			try {
				Socket var1 = NetworkListenThread.func_713_a(this.field_985_b).accept();
				if(var1 != null) {
					HashMap var2 = NetworkListenThread.func_35504_b(this.field_985_b);
					synchronized(var2) {
						InetAddress var3 = var1.getInetAddress();
						if(NetworkListenThread.func_35504_b(this.field_985_b).containsKey(var3) && System.currentTimeMillis() - ((Long)NetworkListenThread.func_35504_b(this.field_985_b).get(var3)).longValue() < 5000L) {
							NetworkListenThread.func_35504_b(this.field_985_b).put(var3, Long.valueOf(System.currentTimeMillis()));
							var1.close();
							continue;
						}

						NetworkListenThread.func_35504_b(this.field_985_b).put(var3, Long.valueOf(System.currentTimeMillis()));
					}

					NetLoginHandler var7 = new NetLoginHandler(this.mcServer, var1, "Connection #" + NetworkListenThread.func_712_b(this.field_985_b));
					NetworkListenThread.func_716_a(this.field_985_b, var7);
				}
			} catch (IOException var6) {
				var6.printStackTrace();
			}
		}

	}
}
