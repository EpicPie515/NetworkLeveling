package net.kjnine.networkleveling;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import net.kjnine.networkleveling.config.ConfigManager;
import net.kjnine.networkleveling.data.DataLoader;
import net.kjnine.networkleveling.data.MessagingAdapter;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.event.EventHandler;

public class NetworkLevelingPlugin extends Plugin implements Listener {
	
	public static NetworkLevelingPlugin inst;
	
	private DataLoader dl;
	private NetworkLevelManager levelManager;
	private ConfigManager configManager;
	private Configuration config;
	private Configuration messages;
	private MessagingAdapter netMessaging;
	
	private double multiplier = 1.0;
	
	public Multimap<UUID, Consumer<ProxiedPlayer>> loginExec = ArrayListMultimap.create();
	
	public Map<String, Integer> serverIds = new HashMap<>();
	
	@Override
	public void onEnable() {
		inst = this;
		configManager = new ConfigManager(this);
		configManager.saveDefaultConfig("config");
		config = configManager.getConfig("config");
		String dt = config.getString("datatype");
		// DB Settings
		Configuration dbSettings = config.getSection("database");
		String address = dbSettings.getString("address"),
				name = dbSettings.getString("name"),
				user = dbSettings.getString("user"),
				pass = dbSettings.getString("pass"),
				table = dbSettings.getString("table");
		int port = dbSettings.getInt("port");
		String nm = config.getString("netmsg");
		Configuration redisConn = config.getSection("redis-connection");
		
		if(nm.equalsIgnoreCase("redis")) {
			String raddress = redisConn.getString("address"),
					rport = redisConn.getString("port"),
					rpass = redisConn.getString("pass");
			netMessaging = new MessagingAdapter.Redis(raddress, rport, rpass);
		} else {
			netMessaging = new MessagingAdapter.PluginMessaging(getProxy());
		}
		
		if(dt.equalsIgnoreCase("mongodb")) {
			dl = new DataLoader.MongoDB(address, port, name, user, pass, table);
		} else if(dt.equalsIgnoreCase("mysql")) {
			dl = new DataLoader.MySQL(address, port, name, user, pass, table, 4, 12, 5000);
		} else {
			File data = new File(getDataFolder(), "data");
			if(!data.exists() || !data.isDirectory()) data.mkdir();
			dl = new DataLoader.FlatFile(data);
		}
		
		multiplier = config.getDouble("xp-multiplier");
		messages = config.getSection("messages");
		NetworkLevel.init(config.getSection("level-groups"));
		
		levelManager = new NetworkLevelManager(this);
		
		levelManager.registerMessaging(netMessaging);
		
		getProxy().getPluginManager().registerListener(this, this);
		
		TestCommands cmds = new TestCommands(this);
		getProxy().getPluginManager().registerCommand(this, cmds);
	}
	
	public void saveConfig() {
		configManager.saveConfig("config", config);
	}
	
	public void reloadConfig() {
		config = configManager.getConfig("config");
		multiplier = config.getDouble("xp-multiplier");
		messages = config.getSection("messages");
		NetworkLevel.init(config.getSection("level-groups"));
	}
	
	public double getMultiplier() {
		return multiplier;
	}
	
	public void setMultiplier(double multiplier) {
		this.multiplier = multiplier;
		config.set("xp-multiplier", this.multiplier);
		saveConfig();
	}
	
	public void setMultiplierDescription(String desc) {
		config.set("messages.multiplier-desc", desc);
		saveConfig();
	}
	
	public DataLoader getDataLoader() {
		return dl;
	}
	
	public NetworkLevelManager getLevelManager() {
		return levelManager;
	}
	
	public Configuration getMessages() {
		return messages;
	}
	
	@EventHandler
	public void onConnected(ServerConnectedEvent e) {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		DataOutputStream dout = new DataOutputStream(bout);
		try {
			dout.writeUTF("UUID");
			UUID u = e.getPlayer().getUniqueId();
			dout.writeLong(u.getMostSignificantBits());
			dout.writeLong(u.getLeastSignificantBits());
			dout.writeUTF("Level");
			dout.writeInt(levelManager.getLevel(u));
			dout.writeUTF("Experience");
			dout.writeLong(levelManager.getExperience(u));
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		netMessaging.sendData(e.getServer().getInfo(), "NLMetadata", bout.toByteArray());
	}
	
	@Override
	public void onDisable() {
		dl.close();
	}
	
}
