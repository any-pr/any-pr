package net.minecraft.client.gui.screens.multiplayer;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class JoinMultiplayerScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int TOP_ROW_BUTTON_WIDTH = 100;
    private static final int LOWER_ROW_BUTTON_WIDTH = 74;
    
    // 🔧 MCRe：内置测试服常量
    private static final String TEST_SERVER_NAME = "测试服 Test Server";
    private static final String TEST_SERVER_IP = "127.0.0.1";
    private static final String TEST_SERVER_IP_WITH_PORT = "127.0.0.1:25565";

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 60);
    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final Screen lastScreen;
    protected ServerSelectionList serverSelectionList;
    private ServerList servers;
    private Button editButton;
    private Button selectButton;
    private Button deleteButton;
    private ServerData editingServer;
    private LanServerDetection.LanServerList lanServerList;
    private LanServerDetection.@Nullable LanServerDetector lanServerDetector;

    public JoinMultiplayerScreen(final Screen lastScreen) {
        super(Component.translatable("multiplayer.title"));
        this.lastScreen = lastScreen;
    }

    /** 判断给定 ServerData 是否为内置测试服 */
    private static boolean isTestServer(final ServerData data) {
        return TEST_SERVER_NAME.equals(data.name) && 
               (TEST_SERVER_IP.equals(data.ip) || TEST_SERVER_IP_WITH_PORT.equals(data.ip));
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        this.servers = new ServerList(this.minecraft);
        this.servers.load();
        
        // 🔧 MCRe：确保内置测试服存在（不可删除、不可重复创建）
        this.ensureTestServerExists();
        
        this.lanServerList = new LanServerDetection.LanServerList();

        try {
            this.lanServerDetector = new LanServerDetection.LanServerDetector(this.lanServerList);
            this.lanServerDetector.start();
        } catch (Exception e) {
            LOGGER.warn("Unable to start LAN server detection: {}", e.getMessage());
        }

        this.serverSelectionList = this.layout
            .addToContents(new ServerSelectionList(this, this.minecraft, this.width, this.layout.getContentHeight(), this.layout.getHeaderHeight(), 36));
        this.serverSelectionList.updateOnlineServers(this.servers);
        LinearLayout footer = this.layout.addToFooter(LinearLayout.vertical().spacing(4));
        footer.defaultCellSetting().alignHorizontallyCenter();
        LinearLayout topFooterButtons = footer.addChild(LinearLayout.horizontal().spacing(4));
        LinearLayout bottomFooterButtons = footer.addChild(LinearLayout.horizontal().spacing(4));
        this.selectButton = topFooterButtons.addChild(Button.builder(Component.translatable("selectServer.select"), button -> {
            ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
            if (entry != null) {
                entry.join();
            }
        }).width(100).build());
        topFooterButtons.addChild(Button.builder(Component.translatable("selectServer.direct"), button -> {
            this.editingServer = new ServerData(I18n.get("selectServer.defaultName"), "", ServerData.Type.OTHER);
            this.minecraft.gui.setScreen(new DirectJoinServerScreen(this, this::directJoinCallback, this.editingServer));
        }).width(100).build());
        topFooterButtons.addChild(
            Button.builder(
                    Component.translatable("selectServer.add"),
                    button -> {
                        this.editingServer = new ServerData("", "", ServerData.Type.OTHER);
                        this.minecraft
                            .gui
                            .setScreen(
                                new ManageServerScreen(this, Component.translatable("manageServer.add.title"), this::addServerCallback, this.editingServer)
                            );
                    }
                )
                .width(100)
                .build()
        );
        this.editButton = bottomFooterButtons.addChild(
            Button.builder(
                    Component.translatable("selectServer.edit"),
                    button -> {
                        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
                        if (entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry) {
                            ServerData current = onlineServerEntry.getServerData();
                            this.editingServer = new ServerData(current.name, current.ip, ServerData.Type.OTHER);
                            this.editingServer.copyFrom(current);
                            this.minecraft
                                .gui
                                .setScreen(
                                    new ManageServerScreen(
                                        this, Component.translatable("manageServer.edit.title"), this::editServerCallback, this.editingServer
                                    )
                                );
                        }
                    }
                )
                .width(74)
                .build()
        );
        this.deleteButton = bottomFooterButtons.addChild(Button.builder(Component.translatable("selectServer.delete"), button -> {
            ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
            if (entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry) {
                ServerData serverData = onlineServerEntry.getServerData();
                // 🔧 MCRe：禁止删除内置测试服
                if (isTestServer(serverData)) {
                    this.minecraft.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("内置测试服不可删除").withStyle(ChatFormatting.RED)
                    );
                    return;
                }
                String serverName = serverData.name;
                if (serverName != null) {
                    Component title = Component.translatable("selectServer.deleteQuestion");
                    Component warning = Component.translatable("selectServer.deleteWarning", serverName);
                    Component yes = Component.translatable("selectServer.deleteButton");
                    Component no = CommonComponents.GUI_CANCEL;
                    this.minecraft.gui.setScreen(new ConfirmScreen(this::deleteCallback, title, warning, yes, no));
                }
            }
        }).width(74).build());
        bottomFooterButtons.addChild(Button.builder(Component.translatable("selectServer.refresh"), button -> this.refreshServerList()).width(74).build());
        bottomFooterButtons.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(74).build());
        JoinMultiplayerScreen var4 = this;
        this.layout.visitWidgets(x$0 -> var4.addRenderableWidget(x$0));
        this.repositionElements();
        this.onSelectedChange();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.serverSelectionList != null) {
            this.serverSelectionList.updateSize(this.width, this.layout);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.lastScreen);
    }

    @Override
    public void tick() {
        super.tick();
        List<LanServer> lanServers = this.lanServerList.takeDirtyServers();
        if (lanServers != null) {
            this.serverSelectionList.updateNetworkServers(lanServers);
        }

        this.pinger.tick();
    }

    @Override
    public void removed() {
        if (this.lanServerDetector != null) {
            this.lanServerDetector.interrupt();
            this.lanServerDetector = null;
        }

        this.pinger.removeAll();
        this.serverSelectionList.removed();
    }

    private void refreshServerList() {
        this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this.lastScreen));
    }

    /** 🔧 MCRe：确保内置测试服存在于服务器列表中 */
    private void ensureTestServerExists() {
        ServerData existing = this.servers.get(TEST_SERVER_IP);
        if (existing == null) {
            existing = this.servers.get(TEST_SERVER_IP_WITH_PORT);
        }
        if (existing == null || !TEST_SERVER_NAME.equals(existing.name)) {
            // 创建测试服条目（使用默认图标，不设置 iconBytes）
            ServerData testServer = new ServerData(TEST_SERVER_NAME, TEST_SERVER_IP, ServerData.Type.OTHER);
            this.servers.add(testServer, false);
            this.servers.save();
        }
    }

    private void deleteCallback(final boolean result) {
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (result && entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry) {
            ServerData serverData = onlineServerEntry.getServerData();
            // 🔧 MCRe：双重保护——禁止删除内置测试服
            if (isTestServer(serverData)) {
                this.minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("内置测试服不可删除").withStyle(ChatFormatting.RED)
                );
                return;
            }
            this.servers.remove(serverData);
            this.servers.save();
            this.serverSelectionList.setSelected((ServerSelectionList.Entry)null);
            this.serverSelectionList.updateOnlineServers(this.servers);
        }

        this.minecraft.gui.setScreen(this);
    }

    private void editServerCallback(final boolean result) {
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (result && entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry) {
            ServerData current = onlineServerEntry.getServerData();
            // 🔧 MCRe：禁止编辑内置测试服的名称/IP（防止绕过重复检测）
            if (isTestServer(current)) {
                this.minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("内置测试服不可修改").withStyle(ChatFormatting.RED)
                );
                return;
            }
            current.name = this.editingServer.name;
            current.ip = this.editingServer.ip;
            current.copyFrom(this.editingServer);
            this.servers.save();
            this.serverSelectionList.updateOnlineServers(this.servers);
        }

        this.minecraft.gui.setScreen(this);
    }

    private void addServerCallback(final boolean result) {
        if (result) {
            // 🔧 MCRe：禁止创建与内置测试服同名同 IP 的服务器
            if (TEST_SERVER_NAME.equals(this.editingServer.name) 
                && (TEST_SERVER_IP.equals(this.editingServer.ip) || TEST_SERVER_IP_WITH_PORT.equals(this.editingServer.ip))) {
                this.minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("已存在同名同 IP 的内置测试服").withStyle(ChatFormatting.RED)
                );
                this.minecraft.gui.setScreen(this);
                return;
            }
            ServerData serverData = this.servers.unhide(this.editingServer.ip);
            if (serverData != null) {
                serverData.copyNameIconFrom(this.editingServer);
                this.servers.save();
            } else {
                this.servers.add(this.editingServer, false);
                this.servers.save();
            }

            this.serverSelectionList.setSelected((ServerSelectionList.Entry)null);
            this.serverSelectionList.updateOnlineServers(this.servers);
        }

        this.minecraft.gui.setScreen(this);
    }

    private void directJoinCallback(final boolean result) {
        if (result) {
            ServerData serverData = this.servers.get(this.editingServer.ip);
            if (serverData == null) {
                this.servers.add(this.editingServer, true);
                this.servers.save();
                this.join(this.editingServer);
            } else {
                this.join(serverData);
            }
        } else {
            this.minecraft.gui.setScreen(this);
        }
    }

    @Override
    public boolean keyPressed(final KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        } else if (event.key() == 294) {
            this.refreshServerList();
            return true;
        } else {
            return false;
        }
    }

    public void join(final ServerData data) {
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(data.ip), data, false, null);
    }

    protected void onSelectedChange() {
        this.selectButton.active = false;
        this.editButton.active = false;
        this.deleteButton.active = false;
        ServerSelectionList.Entry entry = this.serverSelectionList.getSelected();
        if (entry != null && !(entry instanceof ServerSelectionList.LANHeader)) {
            this.selectButton.active = true;
            if (entry instanceof ServerSelectionList.OnlineServerEntry onlineServerEntry) {
                ServerData serverData = onlineServerEntry.getServerData();
                // 🔧 MCRe：内置测试服不可删除、不可编辑
                boolean isTest = isTestServer(serverData);
                this.editButton.active = !isTest;
                this.deleteButton.active = !isTest;
            }
        }
    }

    public ServerStatusPinger getPinger() {
        return this.pinger;
    }

    public ServerList getServers() {
        return this.servers;
    }
}