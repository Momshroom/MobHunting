package one.lindegaard.MobHunting.compatibility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.RegionContainer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StateFlag.State;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import one.lindegaard.MobHunting.MobHunting;

public class WorldGuardHelper implements Listener {

	private static final StateFlag MOBHUNTINGFLAG = new StateFlag("MobHunting", true);
	private static RegionContainer regionContainer;
	private static HashMap<String, String> mobHuntingRegions = new HashMap<String, String>();
	private final static File configFile = new File(MobHunting.getInstance().getDataFolder(), "worldguard_regions.yml");

	public static void addMobHuntingFlag() {
		// adding customflag
		try {
			Field field = DefaultFlag.class.getDeclaredField("flagsList");
			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
			field.setAccessible(true);
			@SuppressWarnings("deprecation")
			List<Flag<?>> flags = new ArrayList<Flag<?>>(Arrays.asList(DefaultFlag.getFlags()));
			flags.add(MOBHUNTINGFLAG);
			field.set(null, flags.toArray(new Flag[flags.size()]));
			WorldGuardPlugin.class.cast(Bukkit.getPluginManager().getPlugin("WorldGuard")).getGlobalStateManager()
					.load();
			regionContainer = WorldGuardCompat.getWorldGuardPlugin().getRegionContainer();
			loadMobHuntingRegions();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// *******************************************************************
	// SAVE / LOAD
	// *******************************************************************
	public static void saveMobHuntingRegions() {
		try {
			YamlConfiguration config = new YamlConfiguration();
			config.options().header(
					"This file is automatically generated. Do NOT edit this file manually or you risk losing data.");
			config.createSection("regions", mobHuntingRegions);
			config.save(configFile);
			MobHunting.getInstance().getMessages().debug("MobHunting-Worldguard regions updated");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void loadMobHuntingRegions() {
		if (!configFile.exists())
			return;
		YamlConfiguration config = new YamlConfiguration();
		try {
			config.load(configFile);

			ConfigurationSection regionSection = config.getConfigurationSection("regions");

			Set<String> regions = regionSection.getKeys(true);
			if (regions == null) {
				return;
			}
			mobHuntingRegions.clear();
			for (String region : regions) {
				String flagValue = regionSection.getString(region);
				mobHuntingRegions.put(region, flagValue);
				for (World world : Bukkit.getWorlds()) {
					Iterator<Entry<String, ProtectedRegion>> i = regionContainer.get(world).getRegions().entrySet()
							.iterator();
					while (i.hasNext()) {
						Entry<String, ProtectedRegion> s = i.next();
						if (s.getKey().equalsIgnoreCase(region)) {
							setCurrentRegionFlag(null, s.getValue(), MOBHUNTINGFLAG, flagValue);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}

	// *******************************************************************
	// getters
	// *******************************************************************

	public static StateFlag getMobHuntingFlag() {
		return MOBHUNTINGFLAG;
	}

	public static RegionContainer getRegionContainer() {
		return regionContainer;
	}

	public static LocalPlayer getLocalPlayer(Player player) {
		return WorldGuardCompat.getWorldGuardPlugin().wrapPlayer(player);
	}

	// *******************************************************************
	// SET / REMOVE FLAG
	// *******************************************************************

	private static State parseInput(String flagValue) {
		if (flagValue.equalsIgnoreCase("allow"))
			return State.ALLOW;
		else if (flagValue.equalsIgnoreCase("deny"))
			return State.DENY;
		else
			return null;
	}

	public static boolean setCurrentRegionFlag(CommandSender sender, ProtectedRegion region, StateFlag stateFlag,
			String flagstate) {
		if (sender != null)
			sender.sendMessage(
					ChatColor.YELLOW + "Region flag MobHunting set on '" + region.getId() 
					+ "' to '" + flagstate + "'");
		region.setFlag(getMobHuntingFlag(), parseInput(flagstate));
		region.setDirty(true);
		mobHuntingRegions.put(region.getId(), flagstate);
		saveMobHuntingRegions();
		String flagstring = "";
		Iterator<Entry<Flag<?>, Object>> i = region.getFlags().entrySet().iterator();
		while (i.hasNext()) {
			Entry<Flag<?>, Object> s = i.next();
			flagstring = flagstring + s.getKey().getName() + ": " + s.getValue();
			if (i.hasNext())
				flagstring = flagstring + ",";
		}
		if (sender != null)
			sender.sendMessage(ChatColor.GRAY + "(Current flags: " + flagstring + ")");

		return true;
	}

	public static boolean removeCurrentRegionFlag(CommandSender sender, ProtectedRegion region, StateFlag stateFlag) {
		region.setFlag(stateFlag, null);
		mobHuntingRegions.remove(region.getId());
		saveMobHuntingRegions();
		if (sender != null)
			sender.sendMessage(ChatColor.YELLOW + "Region flag '" + stateFlag.getName() + "' removed from region '"
					+ region.getId() + "'");
		return true;
	}

	// *******************************************************************
	// EVENTS
	// *******************************************************************
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerMove(final PlayerMoveEvent event) {
		if (!WorldGuardCompat.isEnabledInConfig() || !WorldGuardCompat.isSupported())
			return;
		Player player = event.getPlayer();
		ApplicableRegionSet set = WorldGuardCompat.getWorldGuardPlugin().getRegionManager(player.getWorld())
				.getApplicableRegions(player.getLocation());
		if (set.size() > 0) {
			Iterator<ProtectedRegion> i = set.getRegions().iterator();
			while (i.hasNext()) {
				ProtectedRegion pr = i.next();
				if (pr.getFlags().containsKey(MOBHUNTINGFLAG)) {
					if (!mobHuntingRegions.containsKey(pr.getId()) || !pr.getFlag(MOBHUNTINGFLAG).toString()
							.equalsIgnoreCase(mobHuntingRegions.get(pr.getId()))) {
						MobHunting.getInstance().getMessages().debug(
								"Found unregistered flag or flag with changed State found in region '%s' with value %s",
								pr.getId(), pr.getFlag(MOBHUNTINGFLAG).name());
						mobHuntingRegions.put(pr.getId(), pr.getFlag(MOBHUNTINGFLAG).toString().toLowerCase());
						saveMobHuntingRegions();
					}
				}
			}
		}
	}

	public static boolean isAllowedByWorldGuard(Entity damager, Entity damaged, StateFlag stateFlag,
			boolean defaultValue) {
		// if (damager != null) {
		Player checkedPlayer = null;
		if (MyPetCompat.isMyPet(damager))
			checkedPlayer = MyPetCompat.getMyPetOwner(damager);
		else if (damager instanceof Player)
			checkedPlayer = (Player) damager;
		if (checkedPlayer != null) {
			RegionManager regionManager = WorldGuardCompat.getWorldGuardPlugin()
					.getRegionManager(checkedPlayer.getWorld());
			if (regionManager != null) {
				ApplicableRegionSet set = regionManager.getApplicableRegions(checkedPlayer.getLocation());
				if (set.size() > 0) {
					LocalPlayer localPlayer = WorldGuardCompat.getWorldGuardPlugin().wrapPlayer(checkedPlayer);
					State flag = set.queryState(localPlayer, stateFlag);
					// MobHunting.getInstance().getMessages().debug("testState=%s", set.testState(localPlayer,
					// stateFlag));
					// MobHunting.getInstance().getMessages().debug("FLAG %s defaultValue=%s",
					// stateFlag.getName(), stateFlag.getDefault());
					// State flag = set.getFlag(stateFlag);
					if (flag == null) {
						//MobHunting.getInstance().getMessages().debug("WorldGuard %s flag not defined. Default value = %s", stateFlag.getName(),
						//		defaultValue);
						return defaultValue;
					} else if (flag.equals(State.ALLOW)) {
						return true;
					} else {
						return false;
					}
				} else {
					//MobHunting.getInstance().getMessages().debug("No region here, return default %s=%s", stateFlag.getName(), defaultValue);
					return defaultValue;
				}
			}
		}
		//MobHunting.getInstance().getMessages().debug("WorldGuard return default %s=%s", stateFlag.getName(), defaultValue);
		return defaultValue;
	}

}
