package net.kjnine.networkleveling;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bson.BsonElement;
import org.bson.BsonInt32;
import org.bson.BsonInt64;
import org.bson.BsonString;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kjnine.networkleveling.data.ByteMessage;
import net.kjnine.networkleveling.data.MessagingAdapter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class NetworkLevelManager {

	private NetworkLevelingPlugin pl;
	
	public NetworkLevelManager(NetworkLevelingPlugin pl) {
		this.pl = pl;
	}
	
	public void registerMessaging(MessagingAdapter netmsg) {
		netmsg.registerChannel("NetworkLeveling");
		netmsg.registerChannel("NLMetadata");
		netmsg.registerChannel("NLReturn");
		
		netmsg.addListener("NetworkLeveling", (data) -> {
			Map<String, Object> dataMap = new ByteMessage(data).getDatamap();
			String subchannel = (String) dataMap.get("SubChannel");
			UUID uuid = (UUID) dataMap.get("UUID");
			ServerInfo server = pl.getProxy().getServerInfo((String) dataMap.get("ServerSource"));
			if(subchannel == null) throw new IllegalArgumentException("SubChannel not found in Data");
			if(uuid == null) throw new IllegalArgumentException("No UUID found in Data");
			if(server == null) {
				ProxiedPlayer upl = pl.getProxy().getPlayer(uuid);
				if(upl != null) 
					server = upl.getServer().getInfo();
				else 
					throw new IllegalArgumentException("No Server Source found in Data");
			}
			if(subchannel.equals("GetLevel")) {
				int level = pl.getLevelManager().getLevel(uuid);
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				DataOutputStream dout = new DataOutputStream(bout);
				try {
					dout.writeUTF("SubChannel");
					dout.writeUTF("GetLevel");
					dout.writeUTF("UUID");
					dout.writeLong(uuid.getMostSignificantBits());
					dout.writeLong(uuid.getLeastSignificantBits());
					dout.writeUTF("Level");
					dout.writeInt(level);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				netmsg.sendData(server, "NLReturn", bout.toByteArray());
			} else if(subchannel.equals("SetLevel")) {
				if(!dataMap.containsKey("Level")) throw new IllegalArgumentException("Received SetLevel with no Level Data");
				int level = (int) dataMap.get("Level");
				pl.getLevelManager().setLevel(uuid, level);
			} else if(subchannel.equals("AddExperience")) {
				if(!dataMap.containsKey("Experience")) throw new IllegalArgumentException("Received AddExperience with no Experience Data");
				long toAdd = (long) dataMap.get("Experience");
				pl.getLevelManager().addExperience(uuid, toAdd);
			} else if(subchannel.equals("GetExperience")) {
				long exp = pl.getLevelManager().getExperience(uuid);
				ByteArrayOutputStream bout = new ByteArrayOutputStream();
				DataOutputStream dout = new DataOutputStream(bout);
				try {
					dout.writeUTF("SubChannel");
					dout.writeUTF("GetExperience");
					dout.writeUTF("UUID");
					dout.writeLong(uuid.getMostSignificantBits());
					dout.writeLong(uuid.getLeastSignificantBits());
					dout.writeUTF("GetExperience");
					dout.writeLong(exp);
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				netmsg.sendData(server, "NLReturn", bout.toByteArray());
			}
		});
	}
	
	public int getLevel(UUID uuid) {
		Set<JsonElement> el = pl.getDataLoader().getData("uuid", new BsonString(uuid.toString()));
		int level = 1;
		if(el != null) {
			for(JsonElement j : el) {
				if(j.isJsonObject()) {
					JsonObject jo = j.getAsJsonObject();
					JsonElement levelEl = jo.get("level");
					if(levelEl != null) {
						level = levelEl.getAsInt();
						break;
					}
				}
			}
		}
		if(level < 1) level = 1;
		return level;
	}
	
	public long getExperience(UUID uuid) {
		Set<JsonElement> el = pl.getDataLoader().getData("uuid", new BsonString(uuid.toString()));
		long exp = -1;
		if(el != null) {
			for(JsonElement j : el) {
				if(j.isJsonObject()) {
					JsonObject jo = j.getAsJsonObject();
					JsonElement expEl = jo.get("experience");
					if(expEl != null) {
						exp = expEl.getAsLong();
						break;
					}
				}
			}
		}
		if(exp < 0) exp = 0;
		return exp;
	}
	
	public void setLevel(UUID uuid, int level, String reason) {
		NetworkLevel nl = NetworkLevel.getLevelGroup(level);
		sendLevelingMessage(uuid, MessageType.SETLEVEL, nl.formatLevel(level), reason == null ? "" : "&7(" + reason + "&7)");
		pl.getDataLoader().setData("uuid", new BsonString(uuid.toString()), 
				Arrays.asList(
						new BsonElement("level", new BsonInt32(level)),
						new BsonElement("experience", new BsonInt64(NetworkLevel.getMaximumExperience(level-1)))));
	}
	
	public void setLevel(UUID uuid, int level) {
		setLevel(uuid, level, null);
	}
	
	public void addExperience(UUID uuid, long toAdd) {
		addExperience(uuid, toAdd, null);
	}
	
	public void addExperience(UUID uuid, long toAdd, String reason) {
		Set<JsonElement> el = pl.getDataLoader().getData("uuid", new BsonString(uuid.toString()));
		long exp = -1;
		int level = -1;
		if(el != null) {
			for(JsonElement j : el) {
				if(j.isJsonObject()) {
					JsonObject jo = j.getAsJsonObject();
					JsonElement expEl = jo.get("experience");
					if(expEl != null) {
						exp = expEl.getAsLong();
					}
					JsonElement levelEl = jo.get("level");
					if(levelEl != null) {
						level = levelEl.getAsInt();
					}
					if(exp != -1 && level != -1) break;
				}
			}
		}
		if(exp < 0) exp = 0;
		if(level < 1) level = 1;
		toAdd = (long) (pl.getMultiplier() * toAdd);
		
		if(pl.getMultiplier() != 1.0 && reason == null) {
			reason = pl.getMessages().getString("multiplier-desc");
		} else if(reason != null) {
			reason = "&7(" + reason + "&7)";
		} else if(reason == null) {
			reason = "";
		}
		
		long total = exp + toAdd;
		if(total < 0) total = 0;
		long max = NetworkLevel.getMaximumExperience(level);
		
		sendLevelingMessage(uuid, MessageType.ADDEXPERIENCE, toAdd, reason);
		
		boolean leveledup = total >= max;
		long min = NetworkLevel.getMaximumExperience(level - 1);
		// Block de-leveling
		if(total < min) total = min;
		// Level up until its not past max
		while(total >= max) {
			max = NetworkLevel.getMaximumExperience(++level);
			if(!pl.getMessages().getBoolean("only-send-last-levelup")) {
				NetworkLevel nl = NetworkLevel.getLevelGroup(level);
				sendLevelingMessage(uuid, MessageType.LEVELUP, nl.formatLevel(level));
			}
		}
		if(leveledup && pl.getMessages().getBoolean("only-send-last-levelup")) {
			NetworkLevel nl = NetworkLevel.getLevelGroup(level);
			sendLevelingMessage(uuid, MessageType.LEVELUP, nl.formatLevel(level));
		}
		
		pl.getDataLoader().setData("uuid", new BsonString(uuid.toString()), 
				Arrays.asList(
						new BsonElement("experience", new BsonInt64(total)),
						new BsonElement("level", new BsonInt32(level))));
	}
	
	/**
	 * Will send when next online if offline.
	 */
	public void sendLevelingMessage(UUID uuid, MessageType msg, Object... args) {
		ProxiedPlayer pp = pl.getProxy().getPlayer(uuid);
		if(pp == null || !pp.isConnected()) {
			pl.loginExec.put(uuid, p -> sendLevelingMessage(p, msg, args));
			return;
		}
		sendLevelingMessage(pp, msg, args);
	}
	
	public void sendLevelingMessage(ProxiedPlayer p, MessageType msg, Object... args) {
		String m = pl.getMessages().getString(msg.getConfigPath());
		String f = String.format(m, args);
		f = ChatColor.translateAlternateColorCodes('&', f);
		p.sendMessage(TextComponent.fromLegacyText(f));
	}
	
	public static enum MessageType {
		ADDEXPERIENCE("add-experience"), 
		LEVELUP("level-up"),
		SETLEVEL("set-level");
		
		private String configPath;
		MessageType(String configPath) {
			this.configPath = configPath;
		}
		
		public String getConfigPath() {
			return configPath;
		}
	}
	
}
