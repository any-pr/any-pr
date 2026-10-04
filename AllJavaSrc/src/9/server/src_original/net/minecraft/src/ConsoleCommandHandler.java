package net.minecraft.src;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;
import net.minecraft.server.MinecraftServer;

public class ConsoleCommandHandler {
	private static Logger minecraftLogger = Logger.getLogger("Minecraft");
	private MinecraftServer minecraftServer;

	public ConsoleCommandHandler(MinecraftServer var1) {
		this.minecraftServer = var1;
	}

	public void handleCommand(ServerCommand var1) {
		String var2 = var1.command;
		ICommandListener var3 = var1.commandListener;
		String var4 = var3.getUsername();
		ServerConfigurationManager var5 = this.minecraftServer.configManager;
		if(!var2.toLowerCase().startsWith("help") && !var2.toLowerCase().startsWith("?")) {
			if(var2.toLowerCase().startsWith("list")) {
				var3.log("Connected players: " + var5.getPlayerList());
			} else if(var2.toLowerCase().startsWith("stop")) {
				this.sendNoticeToOps(var4, "Stopping the server..");
				this.minecraftServer.initiateShutdown();
			} else {
				int var6;
				WorldServer var7;
				if(var2.toLowerCase().startsWith("save-all")) {
					this.sendNoticeToOps(var4, "Forcing save..");
					if(var5 != null) {
						var5.savePlayerStates();
					}

					for(var6 = 0; var6 < this.minecraftServer.worldMngr.length; ++var6) {
						var7 = this.minecraftServer.worldMngr[var6];
						var7.saveWorld(true, (IProgressUpdate)null);
					}

					this.sendNoticeToOps(var4, "Save complete.");
				} else if(var2.toLowerCase().startsWith("save-off")) {
					this.sendNoticeToOps(var4, "Disabling level saving..");

					for(var6 = 0; var6 < this.minecraftServer.worldMngr.length; ++var6) {
						var7 = this.minecraftServer.worldMngr[var6];
						var7.levelSaving = true;
					}
				} else if(var2.toLowerCase().startsWith("save-on")) {
					this.sendNoticeToOps(var4, "Enabling level saving..");

					for(var6 = 0; var6 < this.minecraftServer.worldMngr.length; ++var6) {
						var7 = this.minecraftServer.worldMngr[var6];
						var7.levelSaving = false;
					}
				} else {
					String var14;
					if(var2.toLowerCase().startsWith("op ")) {
						var14 = var2.substring(var2.indexOf(" ")).trim();
						var5.func_35479_e(var14);
						this.sendNoticeToOps(var4, "Opping " + var14);
						var5.sendChatMessageToPlayer(var14, "\u00a7eYou are now op!");
					} else if(var2.toLowerCase().startsWith("deop ")) {
						var14 = var2.substring(var2.indexOf(" ")).trim();
						var5.func_35478_f(var14);
						var5.sendChatMessageToPlayer(var14, "\u00a7eYou are no longer op!");
						this.sendNoticeToOps(var4, "De-opping " + var14);
					} else if(var2.toLowerCase().startsWith("ban-ip ")) {
						var14 = var2.substring(var2.indexOf(" ")).trim();
						var5.banIP(var14);
						this.sendNoticeToOps(var4, "Banning ip " + var14);
					} else if(var2.toLowerCase().startsWith("pardon-ip ")) {
						var14 = var2.substring(var2.indexOf(" ")).trim();
						var5.pardonIP(var14);
						this.sendNoticeToOps(var4, "Pardoning ip " + var14);
					} else {
						EntityPlayerMP var15;
						if(var2.toLowerCase().startsWith("ban ")) {
							var14 = var2.substring(var2.indexOf(" ")).trim();
							var5.banPlayer(var14);
							this.sendNoticeToOps(var4, "Banning " + var14);
							var15 = var5.getPlayerEntity(var14);
							if(var15 != null) {
								var15.playerNetServerHandler.kickPlayer("Banned by admin");
							}
						} else if(var2.toLowerCase().startsWith("pardon ")) {
							var14 = var2.substring(var2.indexOf(" ")).trim();
							var5.pardonPlayer(var14);
							this.sendNoticeToOps(var4, "Pardoning " + var14);
						} else {
							int var8;
							if(var2.toLowerCase().startsWith("kick ")) {
								var14 = var2.substring(var2.indexOf(" ")).trim();
								var15 = null;

								for(var8 = 0; var8 < var5.playerEntities.size(); ++var8) {
									EntityPlayerMP var9 = (EntityPlayerMP)var5.playerEntities.get(var8);
									if(var9.username.equalsIgnoreCase(var14)) {
										var15 = var9;
									}
								}

								if(var15 != null) {
									var15.playerNetServerHandler.kickPlayer("Kicked by admin");
									this.sendNoticeToOps(var4, "Kicking " + var15.username);
								} else {
									var3.log("Can\'t find user " + var14 + ". No kick.");
								}
							} else {
								EntityPlayerMP var16;
								String[] var19;
								if(var2.toLowerCase().startsWith("tp ")) {
									var19 = var2.split(" ");
									if(var19.length == 3) {
										var15 = var5.getPlayerEntity(var19[1]);
										var16 = var5.getPlayerEntity(var19[2]);
										if(var15 == null) {
											var3.log("Can\'t find user " + var19[1] + ". No tp.");
										} else if(var16 == null) {
											var3.log("Can\'t find user " + var19[2] + ". No tp.");
										} else if(var15.dimension != var16.dimension) {
											var3.log("User " + var19[1] + " and " + var19[2] + " are in different dimensions. No tp.");
										} else {
											var15.playerNetServerHandler.teleportTo(var16.posX, var16.posY, var16.posZ, var16.rotationYaw, var16.rotationPitch);
											this.sendNoticeToOps(var4, "Teleporting " + var19[1] + " to " + var19[2] + ".");
										}
									} else {
										var3.log("Syntax error, please provice a source and a target.");
									}
								} else {
									String var17;
									int var18;
									if(var2.toLowerCase().startsWith("give ")) {
										var19 = var2.split(" ");
										if(var19.length != 3 && var19.length != 4) {
											return;
										}

										var17 = var19[1];
										var16 = var5.getPlayerEntity(var17);
										if(var16 != null) {
											try {
												var18 = Integer.parseInt(var19[2]);
												if(Item.itemsList[var18] != null) {
													this.sendNoticeToOps(var4, "Giving " + var16.username + " some " + var18);
													int var10 = 1;
													if(var19.length > 3) {
														var10 = this.tryParse(var19[3], 1);
													}

													if(var10 < 1) {
														var10 = 1;
													}

													if(var10 > 64) {
														var10 = 64;
													}

													var16.dropPlayerItem(new ItemStack(var18, var10, 0));
												} else {
													var3.log("There\'s no item with id " + var18);
												}
											} catch (NumberFormatException var12) {
												var3.log("There\'s no item with id " + var19[2]);
											}
										} else {
											var3.log("Can\'t find user " + var17);
										}
									} else if(var2.toLowerCase().startsWith("gamemode ")) {
										var19 = var2.split(" ");
										if(var19.length != 3) {
											return;
										}

										var17 = var19[1];
										var16 = var5.getPlayerEntity(var17);
										if(var16 != null) {
											try {
												var18 = Integer.parseInt(var19[2]);
												var18 = WorldSettings.func_35037_a(var18);
												if(var16.itemInWorldManager.func_35697_a() != var18) {
													this.sendNoticeToOps(var4, "Setting " + var16.username + " to game mode " + var18);
													var16.itemInWorldManager.func_35696_a(var18);
													var16.playerNetServerHandler.sendPacket(new Packet70Bed(3, var18));
												} else {
													this.sendNoticeToOps(var4, var16.username + " already has game mode " + var18);
												}
											} catch (NumberFormatException var11) {
												var3.log("There\'s no game mode with id " + var19[2]);
											}
										} else {
											var3.log("Can\'t find user " + var17);
										}
									} else if(var2.toLowerCase().startsWith("time ")) {
										var19 = var2.split(" ");
										if(var19.length != 3) {
											return;
										}

										var17 = var19[1];

										try {
											var8 = Integer.parseInt(var19[2]);
											WorldServer var20;
											if("add".equalsIgnoreCase(var17)) {
												for(var18 = 0; var18 < this.minecraftServer.worldMngr.length; ++var18) {
													var20 = this.minecraftServer.worldMngr[var18];
													var20.func_32005_b(var20.getWorldTime() + (long)var8);
												}

												this.sendNoticeToOps(var4, "Added " + var8 + " to time");
											} else if("set".equalsIgnoreCase(var17)) {
												for(var18 = 0; var18 < this.minecraftServer.worldMngr.length; ++var18) {
													var20 = this.minecraftServer.worldMngr[var18];
													var20.func_32005_b((long)var8);
												}

												this.sendNoticeToOps(var4, "Set time to " + var8);
											} else {
												var3.log("Unknown method, use either \"add\" or \"set\"");
											}
										} catch (NumberFormatException var13) {
											var3.log("Unable to convert time value, " + var19[2]);
										}
									} else if(var2.toLowerCase().startsWith("say ")) {
										var2 = var2.substring(var2.indexOf(" ")).trim();
										minecraftLogger.info("[" + var4 + "] " + var2);
										var5.sendPacketToAllPlayers(new Packet3Chat("\u00a7d[Server] " + var2));
									} else if(var2.toLowerCase().startsWith("tell ")) {
										var19 = var2.split(" ");
										if(var19.length >= 3) {
											var2 = var2.substring(var2.indexOf(" ")).trim();
											var2 = var2.substring(var2.indexOf(" ")).trim();
											minecraftLogger.info("[" + var4 + "->" + var19[1] + "] " + var2);
											var2 = "\u00a77" + var4 + " whispers " + var2;
											minecraftLogger.info(var2);
											if(!var5.sendPacketToPlayer(var19[1], new Packet3Chat(var2))) {
												var3.log("There\'s no player by that name online.");
											}
										}
									} else if(var2.toLowerCase().startsWith("whitelist ")) {
										this.handleWhitelist(var4, var2, var3);
									} else {
										minecraftLogger.info("Unknown console command. Type \"help\" for help.");
									}
								}
							}
						}
					}
				}
			}
		} else {
			this.printHelp(var3);
		}

	}

	private void handleWhitelist(String var1, String var2, ICommandListener var3) {
		String[] var4 = var2.split(" ");
		if(var4.length >= 2) {
			String var5 = var4[1].toLowerCase();
			if("on".equals(var5)) {
				this.sendNoticeToOps(var1, "Turned on white-listing");
				this.minecraftServer.propertyManagerObj.setProperty("white-list", true);
			} else if("off".equals(var5)) {
				this.sendNoticeToOps(var1, "Turned off white-listing");
				this.minecraftServer.propertyManagerObj.setProperty("white-list", false);
			} else if("list".equals(var5)) {
				Set var6 = this.minecraftServer.configManager.getWhiteListedIPs();
				String var7 = "";

				String var9;
				for(Iterator var8 = var6.iterator(); var8.hasNext(); var7 = var7 + var9 + " ") {
					var9 = (String)var8.next();
				}

				var3.log("White-listed players: " + var7);
			} else {
				String var10;
				if("add".equals(var5) && var4.length == 3) {
					var10 = var4[2].toLowerCase();
					this.minecraftServer.configManager.addToWhiteList(var10);
					this.sendNoticeToOps(var1, "Added " + var10 + " to white-list");
				} else if("remove".equals(var5) && var4.length == 3) {
					var10 = var4[2].toLowerCase();
					this.minecraftServer.configManager.removeFromWhiteList(var10);
					this.sendNoticeToOps(var1, "Removed " + var10 + " from white-list");
				} else if("reload".equals(var5)) {
					this.minecraftServer.configManager.reloadWhiteList();
					this.sendNoticeToOps(var1, "Reloaded white-list from file");
				}
			}

		}
	}

	private void printHelp(ICommandListener var1) {
		var1.log("To run the server without a gui, start it like this:");
		var1.log("   java -Xmx1024M -Xms1024M -jar minecraft_server.jar nogui");
		var1.log("Console commands:");
		var1.log("   help  or  ?               shows this message");
		var1.log("   kick <player>             removes a player from the server");
		var1.log("   ban <player>              bans a player from the server");
		var1.log("   pardon <player>           pardons a banned player so that they can connect again");
		var1.log("   ban-ip <ip>               bans an IP address from the server");
		var1.log("   pardon-ip <ip>            pardons a banned IP address so that they can connect again");
		var1.log("   op <player>               turns a player into an op");
		var1.log("   deop <player>             removes op status from a player");
		var1.log("   tp <player1> <player2>    moves one player to the same location as another player");
		var1.log("   give <player> <id> [num]  gives a player a resource");
		var1.log("   tell <player> <message>   sends a private message to a player");
		var1.log("   stop                      gracefully stops the server");
		var1.log("   save-all                  forces a server-wide level save");
		var1.log("   save-off                  disables terrain saving (useful for backup scripts)");
		var1.log("   save-on                   re-enables terrain saving");
		var1.log("   list                      lists all currently connected players");
		var1.log("   say <message>             broadcasts a message to all players");
		var1.log("   time <add|set> <amount>   adds to or sets the world time (0-24000)");
		var1.log("   gamemode <player> <mode>  sets player\'s game mode (0 or 1)");
	}

	private void sendNoticeToOps(String var1, String var2) {
		String var3 = var1 + ": " + var2;
		this.minecraftServer.configManager.sendChatMessageToAllOps("\u00a77(" + var3 + ")");
		minecraftLogger.info(var3);
	}

	private int tryParse(String var1, int var2) {
		try {
			return Integer.parseInt(var1);
		} catch (NumberFormatException var4) {
			return var2;
		}
	}
}
