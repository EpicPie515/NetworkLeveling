package net.kjnine.networkleveling;

import java.util.HashSet;
import java.util.Set;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.config.Configuration;

public class NetworkLevel {
	
	private static Configuration levelGroups;
	private static Set<NetworkLevel> levels;
	
	public static void init(Configuration lvlgroups) {
		levelGroups = lvlgroups;
		levels = new HashSet<>();
		for(String k : levelGroups.getKeys()) {
			Configuration c = levelGroups.getSection(k);
			NetworkLevel nl = new NetworkLevel(c.getInt("min"), c.getInt("max"), ChatColor.valueOf(c.getString("color")), c.contains("name") ? c.getString("name") : k);
			levels.add(nl);
		}
	}

	public static long getMaximumExperience(int level) {
		 // Sum of first N numbers, times 7
		 if(level < 10)
			 return 7*((level*(level+1))/2);
		 // sum of first N squares for past lvl10 (at lvl10 both methods have same max)
		 long calc = ((level)*(level+1)*((2*level)+1))/6;
		 return calc;
	 }

	public static NetworkLevel getLevelGroup(int level) {
		int min = 100000;
		NetworkLevel nlmin = null;
		int max = 1;
		NetworkLevel nlmax = null;
		for(NetworkLevel l : levels) {
			if(level >= l.getMinLevel() && level < l.getMaxLevel()) return l;
			if(l.getMinLevel() < min) {
				min = l.getMinLevel();
				nlmin = l;
			}
			if(l.getMaxLevel() > max) {
				max = l.getMaxLevel();
				nlmax = l;
			}
		}
		if(level < min) return nlmin;
		if(level >= max) return nlmax;
		return nlmin;
	}
	
	
	private int min, max;
	private ChatColor color;
	private String name;
	private NetworkLevel(int min, int max, ChatColor color, String name) {
		this.min = min;
		this.max = max;
		this.color = color;
		this.name = name;
	}

	public int getMinLevel() {
		return min;
	}

	public int getMaxLevel() {
		return max;
	}

	public ChatColor getColor() {
		return color;
	}

	public String getName() {
		return name;
	}

	public String formatLevel(int level) {
		return String.format("%sLevel %,d", color, level);
	}

	public String formatName() {
		return String.format("%s%s", color, name);
	}
	
}
