package net.kjnine.networkleveling.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class ConfigManager {
	
	private Plugin plugin;
	private ConfigurationProvider yamlProvider;
		
	public ConfigManager(Plugin plugin) {
		this.plugin = plugin;
		yamlProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);
	}
	
	public void saveDefaultConfig(String name) {
		if(!plugin.getDataFolder().exists()) {
			plugin.getDataFolder().mkdir();
		}
		File f = new File(plugin.getDataFolder(), name + ".yml");
		if(f.exists()) return;
		InputStream in = plugin.getResourceAsStream(name + ".yml");
		try {
			Files.copy(in, f.toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** 
	 * @param name the File name (without the .yml)
	 * @param config the Configuration object
	 * @return Whether the save was successful.
	 */
	public boolean saveConfig(String name, Configuration config) {
		File f = new File(plugin.getDataFolder(), name + ".yml");
		try {
			yamlProvider.save(config, f);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	public Configuration getConfig(String name) {
		File f = new File(plugin.getDataFolder(), name + ".yml");
		try {
			return yamlProvider.load(f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
}