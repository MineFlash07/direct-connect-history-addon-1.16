package de.raik.directconnecthistory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.labymod.api.LabyModAddon;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.events.client.gui.screen.ScreenOpenEvent;
import net.labymod.gui.ModGuiMultiplayer;
import net.labymod.gui.ModGuiScreenServerList;
import net.labymod.main.LabyMod;
import net.labymod.settings.elements.ControlElement;
import net.labymod.settings.elements.NumberElement;
import net.labymod.settings.elements.SettingsElement;
import net.labymod.utils.Material;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class DirectConnectHistoryAddon extends LabyModAddon {

    //Settings
    private int historyLength = 5;
    private final ArrayList<String> history = new ArrayList<>();

    private GlobalAddonConfig globalConfig;

    @Override
    public void onEnable() {
        this.globalConfig = new GlobalAddonConfig(this);

        this.api.getEventService().registerListener(this);
    }

    @Override
    public void loadConfig() {
        this.globalConfig.compare();
        JsonObject config = this.getConfig();

        this.historyLength = config.has("amount") ? config.get("amount").getAsInt() : this.historyLength;

        if (!config.has("servers")) {
            this.checkDirectConnect();
            return;
        }
        this.history.clear();
        JsonArray historyConfig = config.getAsJsonArray("servers");
        for (JsonElement historyElement: historyConfig) {
            this.history.add(historyElement.getAsString());
        }

        this.checkDirectConnect();
        this.trimHistory();
    }

    @Subscribe
    public void onGuiOpen(ScreenOpenEvent event) {
        if (!(event.getScreen() instanceof ModGuiScreenServerList)) {
            return;
        }
        event.setScreen(new HistoryServerListScreen((ModGuiMultiplayer) LabyMod.getInstance().getGuiOpenListener().getGuiMultiplayer(), this));
    }

    private void checkDirectConnect() {
        String newDirectConnect = Minecraft.getInstance().gameSettings.lastServer;
        if (!this.getConfig().has("lastServer")) {
            this.getConfig().addProperty("lastServer", newDirectConnect);
            this.globalConfig.save();
            return;
        }
        String lastDirectConnect = this.getConfig().get("lastServer").getAsString();
        this.addServer(lastDirectConnect, newDirectConnect);
    }

    private void trimHistory() {
        while (this.history.size() > this.historyLength) {
            this.history.remove(this.history.size() - 1);
        }
    }

    public void addServer(String lastServer, String newIp) {
        if (newIp.equals(lastServer)) {
            return;
        }
        this.history.remove(newIp);
        if (!lastServer.equals("")) {
            this.history.remove(lastServer);
            this.history.add(0, lastServer);
        }
        this.trimHistory();

        JsonArray servers = new JsonArray();
        this.history.forEach(servers::add);
        this.getConfig().add("servers", servers);
        this.getConfig().addProperty("lastServer", lastServer);

        this.globalConfig.save();
    }

    @Override
    protected void fillSettings(List<SettingsElement> subSettings) {
        subSettings.add(new NumberElement("Length of history", new ControlElement.IconData(Material.BOOK)
                , this.historyLength)
                .setMinValue(1)
                .addCallback(setValue -> {
                    this.historyLength = setValue;
                    this.getConfig().addProperty("amount", setValue);
                    this.globalConfig.save();
                }));
    }

    public String[] getHistoryAsArray() {
        return this.history.toArray(new String[this.history.size()]);
    }
}
