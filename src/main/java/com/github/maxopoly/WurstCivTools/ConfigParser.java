package com.github.maxopoly.WurstCivTools;

import static vg.civcraft.mc.civmodcore.util.ConfigParsing.parseItemMapDirectly;
import static vg.civcraft.mc.civmodcore.util.ConfigParsing.parseTime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;

import vg.civcraft.mc.civmodcore.itemHandling.ItemMap;

import com.github.maxopoly.WurstCivTools.anvil.AnvilHandler;
import com.github.maxopoly.WurstCivTools.effect.PlayerHook;
import com.github.maxopoly.WurstCivTools.effect.PylonFinder;
import com.github.maxopoly.WurstCivTools.effect.WurstEffect;
import com.github.maxopoly.WurstCivTools.tags.LoreTag;
import com.github.maxopoly.WurstCivTools.tags.Tag;

public class ConfigParser {
	private WurstCivTools plugin;
	private WurstManager manager;
	private AnvilHandler anvilHandler;

	public ConfigParser() {
		plugin = WurstCivTools.getPlugin();
	}

	public WurstManager parse() {
		plugin.saveDefaultConfig();
		plugin.reloadConfig();
		FileConfiguration config = plugin.getConfig();
		manager = new WurstManager();
		parseTools(config.getConfigurationSection("tech"));
		parseCustomAnvilFunctionality(config.getConfigurationSection("anvil"));
		plugin.info("Parsed complete config");
		return manager;
	}

	public void parseTools(ConfigurationSection config) {
		for (String key : config.getKeys(false)) {
			ConfigurationSection current = config.getConfigurationSection(key);
			String type = current.getString("type");
			Tag tag = parseTag(current.getConfigurationSection("tag"));
			if (tag == null) {
				plugin.severe("Could not parse tag for effect at "
						+ config.getCurrentPath());
				continue;
			}
			WurstEffect effect;
			switch (type) {
			case "PYLONFINDER":
				if (current.getBoolean("enabled",false) == false){
					plugin.info("Pylonfinder disabled, skipping...");
					continue;
				}
				if (!Bukkit.getPluginManager().isPluginEnabled("FactoryMod")) {
					plugin.severe("Attempted to load Pylonfinder tool, but FactoryMod is not installed on this server");
					continue;
				}
				boolean showNonRunning = current.getBoolean("show_non_running",
						true);
				boolean showUpgrading = current.getBoolean("show_upgrading",
						true);
				long cd = parseTime(current.getString("update_cooldown", "5s")) * 50L;
				effect = new PylonFinder(showNonRunning, showUpgrading, cd);
				plugin.info("Parsed Pylonfinder tool, show_non_running:"
						+ showNonRunning + ", showUpgrading:" + showUpgrading
						+ ", cooldown:" + cd);
				break;
			case "PLAYERHOOK":
				if (current.getBoolean("enabled",false) == false){
					plugin.info("PlayerHook disabled, skipping...");
					continue;
				}
				boolean prevent_swords = current.getBoolean("prevent_use_with_swords", false);
				boolean prevent_axes = current.getBoolean("prevent_use_with_axes", false);
				boolean prevent_bows = current.getBoolean("prevent_use_with_bows", false);
				int stop_count = current.getInt("hooks_to_stop_movement", 2); if(stop_count<=0){stop_count=2;}
				double speed_change = current.getDouble("hook_speed_change",-0.05D);
				effect = new PlayerHook(prevent_swords,prevent_axes,prevent_bows,stop_count, speed_change);
				plugin.info("Parsed PlayerHook tool, swords:" + prevent_swords +
						", axes:" + prevent_axes + ", bows:" + prevent_bows + 
						", stop_count:" + stop_count + ", speed_change:" +speed_change);
				break;

			default:
				plugin.severe("Could not identify effect type " + type + " at "
						+ config.getCurrentPath());
				effect = null;

			}
			if (effect == null) {
				plugin.severe("Could not load effect from config at "
						+ config.getCurrentPath());
				continue;
			}
			tag.setEffect(effect);
			manager.addTag(tag);
		}
	}

	public Tag parseTag(ConfigurationSection config) {
		if (config == null) {
			return null;
		}
		Tag result;
		String type = config.getString("type");
		if (type == null) {
			plugin.severe("No type specified for tag at"
					+ config.getCurrentPath());
			return null;
		}
		String matString = config.getString("material");
		if (matString == null) {
			plugin.severe("No material specified for tag at"
					+ config.getCurrentPath());
			return null;
		}
		Material mat = Material.matchMaterial(matString);
		switch (type.toLowerCase()) {
		case "lore":
			String lore = config.getString("lore");
			result = new LoreTag(mat, lore);
			break;
		default:
			plugin.severe("Could not parse tag type " + type);
			result = null;
			break;
		}
		return result;
	}
	
	public void parseCustomAnvilFunctionality(ConfigurationSection config) {
		if (config == null) {
			return;
		}
		ConfigurationSection materialSection = config.getConfigurationSection("repairMaterials");
		if (materialSection == null) {
			plugin.warning("Could not find repair material section, skipping enabling custom anvil functionality");
			return;
		}
		Map <ItemMap, Double> repairValues = new HashMap<ItemMap, Double>();
		for(String key : materialSection.getKeys(false)) {
			ConfigurationSection current = materialSection.getConfigurationSection(key);
			ItemMap item = parseItemMapDirectly(current.getConfigurationSection("item"));
			if (item.getTotalItemAmount() == 0) {
				plugin.warning("No item specified for custom repair value specification at " + current.getCurrentPath());
				continue;
			}
			double value = current.getDouble("value", 1.0);
			repairValues.put(item, value);
		}
		
		ConfigurationSection enchantSection = config.getConfigurationSection("enchantCosts");
		if (enchantSection == null) {
			plugin.warning("Could not find enchants section, skipping enabling custom anvil functionality");
			return;
		}
		Map <Enchantment, Double> enchantCosts = new HashMap<Enchantment, Double>();
		for(String key : enchantSection.getKeys(false)) {
			ConfigurationSection current = enchantSection.getConfigurationSection(key);
			String enchantName = current.getString("enchant");
			if (enchantName == null) {
				plugin.warning("No enchant specified for custom enchant weight at " + current.getCurrentPath() + ". Skipping it");
				continue;
			}
			Enchantment enchant = Enchantment.getByName(enchantName);
			if (enchant == null) {
				plugin.warning("Invalid enchant name found at " + current.getCurrentPath() + ". Skipping it");
				continue;
			}
			double value = current.getDouble("value", 1.0);
			enchantCosts.put(enchant, value);
		}
		List <String> blacklisted = config.getStringList("blacklistedLore");
		boolean scaleWithMissingDura = config.getBoolean("scaleWithMissingDura", true);
		anvilHandler = new AnvilHandler(repairValues, enchantCosts, scaleWithMissingDura, blacklisted);
	}
	
	public AnvilHandler getAnvilHandler() {
		return anvilHandler;
	}

}
