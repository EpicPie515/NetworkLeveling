package net.kjnine.networkleveling;

import java.util.Arrays;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class TestCommands extends Command {

	private NetworkLevelingPlugin pl;
	
	public TestCommands(NetworkLevelingPlugin pl) {
		super("netlevel", "netlevel.test", "nl");
		this.pl = pl;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {
		if(!sender.hasPermission(getPermission())) {
			sender.sendMessage(new TextComponent("No Permission"));
			return;
		}
		if(args.length == 0) {
			sender.sendMessage(new TextComponent("/nl <addxp|setlvl|getlvl|getxp|multiply|desc|reload>"));
			return;
		}
		switch(args[0].toLowerCase()) {
		case "addxp":
		case "setlvl":
			subcommandSetPlayer(sender, args);
			break;
		case "getlvl":
		case "getxp":
			subcommandGetPlayer(sender, args);
			break;
		case "multiply":
			subcommandMultiplier(sender, args);
			break;
		case "desc":
			subcommandDescription(sender, args);
			break;
		case "reload":
			pl.reloadConfig();
			sender.sendMessage(new TextComponent("[NetworkLeveling] Config Reloaded"));
			break;
		default:
			sender.sendMessage(new TextComponent("[NetworkLeveling] Unknown SubCommand"));
			sender.sendMessage(new TextComponent("/nl <addxp|setlvl|getlvl|getxp|multiply|desc|reload>"));
		}
	}
	
	private void subcommandSetPlayer(CommandSender sender, String[] args) {
		if(args.length <= 2) {
			sender.sendMessage(new TextComponent("/nl " + args[0] + " <player> <number> [reason]"));
			return;
		}
		ProxiedPlayer t = pl.getProxy().getPlayer(args[1]);
		if(t == null || !t.isConnected()) {
			sender.sendMessage(new TextComponent("[NetworkLeveling] Unknown Player"));
			return;
		}
		long num = Long.parseLong(args[2]);
		String r = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
		if(args[0].equalsIgnoreCase("addxp")) {
			pl.getLevelManager().addExperience(t.getUniqueId(), num, r);
			sender.sendMessage(new TextComponent(String.format("[NetworkLeveling] Added %+,d XP to %s (%s)", num, t.getDisplayName(), r)));
		} else if(args[0].equalsIgnoreCase("setlvl")) {
			pl.getLevelManager().setLevel(t.getUniqueId(), (int)num, r);
			sender.sendMessage(new TextComponent(String.format("[NetworkLeveling] Set Level of %s to %,d (%s)", t.getDisplayName(), num, r)));
		}
	}
	
	private void subcommandGetPlayer(CommandSender sender, String[] args) {
		if(args.length <= 1) {
			sender.sendMessage(new TextComponent("/nl " + args[0] + " <player>"));
			return;
		}
		ProxiedPlayer t = pl.getProxy().getPlayer(args[1]);
		if(t == null || !t.isConnected()) {
			sender.sendMessage(new TextComponent("[NetworkLeveling] Unknown Player"));
			return;
		}
		if(args[0].equalsIgnoreCase("getxp")) {
			long exp = pl.getLevelManager().getExperience(t.getUniqueId());
			sender.sendMessage(new TextComponent(String.format("[NetworkLeveling] %s has %,d Total XP", t.getDisplayName(), exp)));
		} else if(args[0].equalsIgnoreCase("getlvl")) {
			int lvl = pl.getLevelManager().getLevel(t.getUniqueId());
			NetworkLevel nl = NetworkLevel.getLevelGroup(lvl);
			sender.sendMessage(TextComponent.fromLegacyText(String.format("[NetworkLeveling] %s is ", t.getDisplayName(), nl.formatLevel(lvl))));
		}
	}
	
	private void subcommandMultiplier(CommandSender sender, String[] args) {
		if(args.length <= 1) {
			sender.sendMessage(new TextComponent("/nl " + args[0] + " <multiplier>"));
			return;
		}
		double num = Double.parseDouble(args[1]);
		pl.setMultiplier(num);
		sender.sendMessage(new TextComponent(String.format("[NetworkLeveling] Set XP Multiplier to %,.01d")));
	}
	
	private void subcommandDescription(CommandSender sender, String[] args) {
		if(args.length <= 1) {
			sender.sendMessage(new TextComponent("/nl " + args[0] + " <description>"));
			return;
		}
		String[] subArr = Arrays.copyOfRange(args, 1, args.length);
		sender.sendMessage(TextComponent.fromLegacyText(
				String.format("[NetworkLeveling] Set XP Multiplier Description to \"%s\"",
						ChatColor.translateAlternateColorCodes('&', String.join(" ", subArr)))));
	}
	
	
}
