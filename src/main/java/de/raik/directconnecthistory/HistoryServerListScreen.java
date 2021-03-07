package de.raik.directconnecthistory;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.labymod.core.LabyModCore;
import net.labymod.gui.ModGuiMultiplayer;
import net.labymod.gui.elements.DropDownMenu;
import net.labymod.main.LabyMod;
import net.labymod.utils.DrawUtils;
import net.labymod.utils.ModColor;
import net.labymod.utils.manager.ServerInfoRenderer;
import net.minecraft.client.gui.screen.ConnectingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

public class HistoryServerListScreen extends Screen {

    private ModGuiMultiplayer screenBefore;
    private ServerData serverData;
    private TextFieldWidget textField;
    private DropDownMenu<String> lastServerDropdown;
    private long lastUpdate = 0L;
    private long updateCooldown = 2000L;
    private ServerInfoRenderer serverInfoRenderer;

    private String lastServer;
    //I HATE RESPONSIVE GUIS
    private boolean toSmall;
    private DirectConnectHistoryAddon addon;

    protected HistoryServerListScreen(ModGuiMultiplayer screenBefore, DirectConnectHistoryAddon addon) {
        super(new TranslationTextComponent("selectServer.direct"));
        this.addon = addon;
        this.screenBefore = screenBefore;
        this.serverData = new ServerData(I18n.format("selectServer.defaultName"), "", false);
    }

    @Override
    public void tick() {
        super.tick();
        this.textField.tick();
        if (!LabyMod.getSettings().directConnectInfo || this.textField.getText().replace(" ", "").isEmpty()) {
            this.serverInfoRenderer = new ServerInfoRenderer(this.textField.getText(), null);
            this.lastUpdate = -1L;
            return;
        }
        if (this.lastUpdate + this.updateCooldown >= System.currentTimeMillis()) {
            return;
        }
        this.lastUpdate = System.currentTimeMillis();
        LabyModCore.getServerPinger().pingServer(null, this.lastUpdate, this.textField.getText(), pingerData -> {
            if (pingerData == null || pingerData.getTimePinged() == this.lastUpdate) {
                this.serverInfoRenderer = new ServerInfoRenderer(this.textField.getText(), pingerData);
            }
        });
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.getListener() != this.textField || keyCode != 257 && keyCode != 335) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        this.connectToServer();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.lastServerDropdown.onClick((int) mouseX, (int) mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void closeScreen() {
        this.minecraft.displayGuiScreen(this.screenBefore);
    }

    @Override
    public void onClose() {
        this.minecraft.keyboardListener.enableRepeatEvents(false);
        this.minecraft.gameSettings.lastServer = this.textField.getText();
        this.minecraft.gameSettings.saveOptions();
        LabyModCore.getServerPinger().closePendingConnections();
        this.addon.addServer(this.lastServer, this.textField.getText());
    }

    @Override
    protected void init() {
        super.init();
        this.minecraft.keyboardListener.enableRepeatEvents(true);
        this.buttons.clear();
        this.toSmall = this.height / 4 + 96 + 12 < 220;
        this.addButton(new Button(this.width / 2 - 100, toSmall ? 195 : this.height / 4 + 96 + 12, 200, 20
                , ITextComponent.getTextComponentOrEmpty(I18n.format("selectServer.select", new Object[0])), press -> {
                if (!this.lastServerDropdown.isOpen()) {
                    this.connectToServer();
                }
        }));
        this.addButton(new Button(this.width / 2 - 100, toSmall ? 216 : this.height / 4 + 120 + 12, 200, 20
                , ITextComponent.getTextComponentOrEmpty(I18n.format("gui.cancel", new Object[0])), press -> {
            if (!this.lastServerDropdown.isOpen()) {
                this.minecraft.displayGuiScreen(this.screenBefore);
            }
        }));
        this.textField = new TextFieldWidget(LabyModCore.getMinecraft().getFontRenderer(), this.width / 2 - 100, 116 - (toSmall ? 19 : 0), 200, 20, ITextComponent.getTextComponentOrEmpty(""));
        this.textField.setMaxStringLength(128);
        this.textField.setFocused2(true);
        this.lastServer = this.minecraft.gameSettings.lastServer;
        this.textField.setText(this.lastServer);
        this.textField.setResponder(change -> this.buttons.get(0).active = this.textField.getText().length() > 0 && this.textField.getText().split(":").length > 0 && this.textField.getText().indexOf(32) == -1);
        this.children.add(this.textField);
        this.setFocusedDefault(this.textField);
        this.buttons.get(0).active = this.textField.getText().length() > 0 && this.textField.getText().split(":").length > 0 && this.textField.getText().indexOf(32) == -1;
        String[] history = this.addon.getHistoryAsArray();
        this.lastServerDropdown = new DropDownMenu<String>("Server history", this.width / 2 - 100, 160 - (toSmall ? 19 : 0), 200, 20).fill(history);
        if (history.length >= 1) {
            this.lastServerDropdown.setSelected(history[0]);
        }
        this.lastServerDropdown.setMaxY(this.height);
        this.addButton(new Button(this.width / 2 - 100, 190 - (toSmall ? 19 : 0), 100, 20, ITextComponent.getTextComponentOrEmpty("Paste")
                , press -> {
            if (!this.lastServerDropdown.isOpen()) {
                this.textField.setText(this.lastServerDropdown.getSelected());
            }
        }));
        this.addButton(new Button(this.width / 2, 190 - (toSmall ? 19 : 0), 100, 20, ITextComponent.getTextComponentOrEmpty("Join")
                , press -> {
            if (!this.lastServerDropdown.isOpen()) {
                this.textField.setText(this.lastServerDropdown.getSelected());
                this.connectToServer();
            }
        }));
        this.buttons.get(2).active = this.buttons.get(3).active = history.length > 0;
    }

    private void connectToServer() {
        this.serverData.serverIP = this.textField.getText();
        if (LabyModCore.getMinecraft().getWorld() != null) {
            LabyModCore.getMinecraft().getWorld().sendQuittingDisconnectingPacket();
            this.minecraft.loadWorld((ClientWorld) null);
        }
        if (LabyMod.getInstance().isInGame()) {
            LabyMod.getInstance().onQuit();
        }

        this.minecraft.displayGuiScreen(new ConnectingScreen(this, this.minecraft, this.serverData));
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        drawCenteredString(matrixStack, LabyModCore.getMinecraft().getFontRenderer(), I18n.format("selectServer.direct", new Object[0]), this.width / 2, 20, 16777215);
        drawString(matrixStack, LabyModCore.getMinecraft().getFontRenderer(), I18n.format("addServer.enterIp", new Object[0]), this.width / 2 - 100, 100, 10526880);
        this.textField.render(matrixStack, mouseX, mouseY, partialTicks);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
        this.lastServerDropdown.draw(matrixStack, mouseX, mouseY);
        if (this.serverInfoRenderer != null && this.lastUpdate != -1L) {
            DrawUtils drawUtils = LabyMod.getInstance().getDrawUtils();
            int leftBound = this.width / 2 - 150;
            int rightBound = this.width / 2 + 150;
            int posY = 44;
            int height = 30;
            drawUtils.drawRectangle(matrixStack, leftBound, posY - 4, rightBound, posY + 6 + height, -2147483648);
            this.serverInfoRenderer.drawEntry(matrixStack, leftBound + 3, posY, rightBound - leftBound, mouseX, mouseY);
            int stateColorR = this.serverInfoRenderer.canReachServer() ? 105 : 240;
            int stateColorG = this.serverInfoRenderer.canReachServer() ? 240 : 105;
            int stateColorB = 105;
            double total = rightBound - leftBound;
            double barPercent = total / (double)this.updateCooldown * (double)(System.currentTimeMillis() - this.lastUpdate);
            if (barPercent > total) {
                barPercent = total;
            }

            int colorPercent = (int)Math.round(155.0D / (double)this.updateCooldown * (double)(System.currentTimeMillis() - this.lastUpdate - 100L));
            drawUtils.drawRectangle(matrixStack, leftBound, posY - 6, rightBound, posY - 5, -2147483648);
            drawUtils.drawRectangle(matrixStack, leftBound, posY - 6, rightBound, posY - 5, ModColor.toRGB(stateColorR, stateColorG, stateColorB, 155 - colorPercent));
            drawUtils.drawRect(matrixStack, leftBound, posY - 6, (double)leftBound + barPercent, posY - 5, ModColor.toRGB(stateColorR, stateColorG, stateColorB, 150));
            drawUtils.drawGradientShadowTop(posY - 4, leftBound, rightBound);
            drawUtils.drawGradientShadowBottom(posY + 6 + height, leftBound, rightBound);
        }
    }
}

