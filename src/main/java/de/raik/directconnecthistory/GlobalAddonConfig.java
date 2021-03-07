package de.raik.directconnecthistory;

import com.google.gson.*;
import net.labymod.api.LabyModAddon;

import java.io.*;

public class GlobalAddonConfig {

    private static final String CONFIG_DIRECTORY = "addons-config-global";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final JsonParser JSON_PARSER = new JsonParser();

    private final File configFile;
    private final LabyModAddon addon;
    private JsonObject internalConfig;

    public GlobalAddonConfig(LabyModAddon addon) {
        this.addon = addon;
        this.configFile = new File("LabyMod/" + CONFIG_DIRECTORY, addon.about.name + ".json");

        File directory = this.configFile.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        this.loadConfig();
    }

    private void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(this.configFile)))) {
            this.internalConfig = (JsonObject) JSON_PARSER.parse(reader);
        } catch (IOException | JsonParseException | ClassCastException exception) {
            this.internalConfig = new JsonObject();
        }
        this.save();
    }

    public void compare() {
        this.internalConfig.entrySet().forEach(entry -> {
            if (!this.addon.getConfig().has(entry.getKey())) {
                this.addon.getConfig().add(entry.getKey(), entry.getValue());
            }
        });
    }

    public void save() {
        this.addon.getConfig().entrySet().forEach(entry -> this.internalConfig.add(entry.getKey(), entry.getValue()));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(this.configFile))) {
            writer.write(GSON.toJson(this.addon.getConfig()));
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

}
