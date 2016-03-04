package de.blablubbabc.homestations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.material.Button;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import de.blablubbabc.homestations.utils.FireworkEffectPlayer;
import de.blablubbabc.homestations.utils.Log;
import de.blablubbabc.homestations.utils.SoftBlockLocation;
import de.blablubbabc.homestations.utils.Utils;

public class HomeStations extends JavaPlugin implements Listener {

	public static HomeStations instance;

	private final Vector toBlockMid = new Vector(0.5, 0, 0.5);

	private DataStore dataStore;
	private YamlConfiguration homesConfig;
	private Set<SoftBlockLocation> spawnStations;
	private SoftBlockLocation mainSpawnStation;

	private FireworkEffectPlayer fplayer;

	private FireworkEffect fe1;
	private FireworkEffect fe2;

	private Vector upVelocity;
	private long teleportDelay;

	private double maxUpEffectRange;
	private double upEffectDistance;

	private double downEffectOffset;
	private double teleportYOffset;

	@Override
	public void onEnable() {
		instance = this;

		// init Log
		Log.init(this);

		// read config
		FileConfiguration config = this.getConfig();

		// effects:
		ConfigurationSection effect1Section = config.getConfigurationSection("Firework Effect 1");
		fe1 = getEffect(effect1Section);
		if (fe1 == null) {
			fe1 = FireworkEffect.builder().withColor(Color.ORANGE).withTrail().withFade(Color.RED).build();
			writeEffectSection(effect1Section != null ? effect1Section : config.createSection("Firework Effect 1"), fe1);
		}
		ConfigurationSection effect2Section = config.getConfigurationSection("Firework Effect 2");
		fe2 = getEffect(effect2Section);
		if (fe2 == null) {
			fe2 = FireworkEffect.builder().withColor(Color.YELLOW).build();
			writeEffectSection(effect2Section != null ? effect2Section : config.createSection("Firework Effect 2"), fe2);
		}

		// yVelocity:
		double yVelocity = config.getDouble("Upward.Teleport.Y Velocity", 2.0D);
		upVelocity = new Vector(0, yVelocity, 0);
		config.set("Upward.Teleport.Y Velocity", yVelocity);

		// teleport delay:
		teleportDelay = config.getLong("Upward.Teleport.Delay in Ticks", 15L);
		config.set("Upward.Teleport.Delay in Ticks", teleportDelay);

		// max teleport effect range:
		maxUpEffectRange = config.getDouble("Upward.Effect.Maximum Range", 255.0D);
		config.set("Upward.Effect.Maximum Range", maxUpEffectRange);

		// max teleport effect range:
		upEffectDistance = config.getDouble("Upward.Effect.Distance between Fireworks", 2.5D);
		config.set("Upward.Effect.Distance between Fireworks", upEffectDistance);

		// down effect offset:
		downEffectOffset = config.getDouble("Downward.Effect.Offset", 3.0D);
		config.set("Downward.Effect.Offset", downEffectOffset);

		// teleport y offset:
		teleportYOffset = config.getDouble("Downward.Teleport.Y Offset", 3.0D);
		config.set("Downward.Teleport.Y Offset", teleportYOffset);

		// save:
		this.saveConfig();

		// initialize DataStore:
		dataStore = new DataStore();

		// load spawn stations locations:
		homesConfig = YamlConfiguration.loadConfiguration(new File(DataStore.homesFilePath));
		spawnStations = new HashSet<SoftBlockLocation>(SoftBlockLocation.getFromStringList(homesConfig.getStringList("Homes.Spawn Stations")));
		mainSpawnStation = SoftBlockLocation.getFromString(homesConfig.getString("Homes.Main Spawn Station"));

		Bukkit.getServer().getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				// validate all spawn stations:
				Iterator<SoftBlockLocation> spawnLocs = spawnStations.iterator();
				while (spawnLocs.hasNext()) {
					SoftBlockLocation softLocation = spawnLocs.next();
					Location location = softLocation.getBukkitLocation();
					if (location == null || !isLowerStationButton(location.getBlock())) {
						Log.warning("Invalid spawn station found (" + softLocation.toString() + "). Removing it now.");
						spawnLocs.remove();
					}
				}

				// load and validate spawn station:
				if (mainSpawnStation != null) {
					Location mainLocation = mainSpawnStation.getBukkitLocation();
					if (mainLocation == null || !isLowerStationButton(mainLocation.getBlock())) {
						Log.warning("Invalid main spawn station (" + mainSpawnStation.toString() + "). Removing it now.");
						mainSpawnStation = null;
					} else if (!spawnStations.contains(mainSpawnStation)) {
						// add to spawn stations list:
						spawnStations.add(mainSpawnStation);
					}

				}

				// save:
				saveSpawnStations();
			}
		}, 1L);

		// firework player:
		fplayer = new FireworkEffectPlayer();

		// register listeners:
		Bukkit.getServer().getPluginManager().registerEvents(this, this);

		// load data for all online players:
		for (Player player : Bukkit.getOnlinePlayers()) {
			// get his player data, forcing it to initialize if we've never seen him before
			dataStore.getPlayerData(player);
		}
	}

	@Override
	public void onDisable() {
		instance = null;
	}

	private static FireworkEffect getEffect(ConfigurationSection section) {
		if (section == null) return null;
		List<Color> colors = getColorList(section.getStringList("Colors"));
		if (colors.isEmpty()) return null;
		List<Color> fadeColors = getColorList(section.getStringList("Fade Colors"));
		boolean flicker = section.getBoolean("Flicker", false);
		boolean trail = section.getBoolean("Trail", false);

		Builder builder = FireworkEffect.builder().withColor(colors);
		if (!fadeColors.isEmpty()) builder.withFade(fadeColors);
		if (flicker) builder.withFlicker();
		if (trail) builder.withTrail();

		return builder.build();
	}

	private static void writeEffectSection(ConfigurationSection section, FireworkEffect effect) {
		if (section == null || effect == null) return;
		section.set("Colors", getStringList(effect.getColors()));
		section.set("Fade Colors", getStringList(effect.getFadeColors()));
		section.set("Flicker", effect.hasFlicker());
		section.set("Trail", effect.hasTrail());
	}

	private static List<Color> getColorList(List<String> stringList) {
		List<Color> colors = new ArrayList<Color>();
		if (stringList != null) {
			for (String string : stringList) {
				if (string == null) continue;
				String[] split = string.split(";");
				if (split.length != 3) continue;
				Integer red = Utils.parseInteger(split[0]);
				Integer green = Utils.parseInteger(split[1]);
				Integer blue = Utils.parseInteger(split[2]);

				if (red == null || blue == null || green == null) continue;

				Color color = Color.fromRGB(getColorInt(red), getColorInt(green), getColorInt(blue));
				colors.add(color);
			}
		}
		return colors;
	}

	private static int getColorInt(int colorInt) {
		return Math.max(0, Math.min(255, colorInt));
	}

	private static List<String> getStringList(List<Color> colorList) {
		List<String> strings = new ArrayList<String>();
		if (colorList != null) {
			for (Color color : colorList) {
				if (color == null) continue;
				String string = color.getRed() + ";" + color.getGreen() + ";" + color.getBlue();
				strings.add(string);
			}
		}
		return strings;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command only be run as player.");
			return true;
		}

		Player player = (Player) sender;
		if (!player.hasPermission("homestation.admin")) {
			player.sendMessage(dataStore.getMessage(Message.NoPermission));
			return true;
		}

		if (args.length == 1) {
			Location location = player.getLocation();
			if (args[0].equalsIgnoreCase("setMainSpawn")) {
				if (!this.isLowerStationButton(location.getBlock())) {
					player.sendMessage(dataStore.getMessage(Message.ThisIsNoStation));
					return true;
				}
				mainSpawnStation = new SoftBlockLocation(location);
				// just in cases this wasn't already a spawn station before; set will make sure that it is only kept
				// once:
				spawnStations.add(mainSpawnStation);
				player.sendMessage(dataStore.getMessage(Message.MainSpawnStationSet));
				this.saveSpawnStations();
				return true;
			} else if (args[0].equalsIgnoreCase("addSpawn")) {
				if (!this.isLowerStationButton(location.getBlock())) {
					player.sendMessage(dataStore.getMessage(Message.ThisIsNoStation));
					return true;
				}
				SoftBlockLocation softLocation = new SoftBlockLocation(location);
				// set will make sure that it is only kept once:
				spawnStations.add(softLocation);

				player.sendMessage(dataStore.getMessage(Message.SpawnStationAdded));
				this.saveSpawnStations();
				return true;
			}
		}
		return false;
	}

	public boolean isLowerStationButton(Block buttonB) {
		return this.getStationFaceForLowerStationButton(buttonB) != null;
	}

	public boolean isHigherStationButton(Block buttonT) {
		return this.getStationFaceHigherStationButton(buttonT) != null;
	}

	// checks for the location above the emerald block, the lower button. Returns the stations direction, or null if not
	// a valid station:
	public BlockFace getStationFaceForLowerStationButton(Block buttonB) {
		if (buttonB == null) return null;
		if (buttonB.getType() != Material.STONE_BUTTON) {
			return null;
		}
		Block emerald = buttonB.getRelative(BlockFace.DOWN);
		if (emerald.getType() != Material.EMERALD_BLOCK) {
			return null;
		}
		Block buttonT = buttonB.getRelative(BlockFace.UP);
		if (buttonT.getType() != Material.STONE_BUTTON) {
			return null;
		}
		Button buttonBA = (Button) buttonB.getState().getData();

		BlockFace stationDirection = buttonBA.getAttachedFace();

		Block lapisB = buttonB.getRelative(stationDirection);
		if (lapisB.getType() != Material.LAPIS_BLOCK) {
			return null;
		}
		Button buttonTA = (Button) buttonT.getState().getData();
		Block lapisT = buttonT.getRelative(buttonTA.getAttachedFace());
		if (lapisT.getType() != Material.LAPIS_BLOCK || !lapisT.equals(lapisB.getRelative(BlockFace.UP))) {
			return null;
		}
		Block redstone = lapisT.getRelative(BlockFace.UP);
		if (redstone.getType() != Material.REDSTONE_BLOCK) {
			return null;
		}

		return stationDirection;
	}

	// checks for the upper station button. Returns the stations direction, or null if not a valid station:
	public BlockFace getStationFaceHigherStationButton(Block buttonT) {
		if (buttonT == null) return null;
		return this.getStationFaceForLowerStationButton(buttonT.getRelative(BlockFace.DOWN));
	}

	public void saveSpawnStations() {
		homesConfig.set("Homes.Spawn Stations", SoftBlockLocation.toStringList(spawnStations));
		homesConfig.set("Homes.Main Spawn Station", mainSpawnStation != null ? mainSpawnStation.toString() : "not set");
		try {
			homesConfig.save(DataStore.homesFilePath);
		} catch (IOException e) {
			Log.severe("Unable to write to the configuration file at \"" + DataStore.homesFilePath + "\"");
		}
	}

	// when a player successfully joins the server...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	void onPlayerJoin(PlayerJoinEvent event) {
		// get his player data, forcing it to initialize if we've never seen him before
		dataStore.getPlayerData(event.getPlayer());
	}

	// when a player quits...
	@EventHandler(priority = EventPriority.HIGHEST)
	void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();

		// drop player data from memory
		dataStore.clearCachedPlayerData(playerId);
	}

	// when a player presses a button...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	void onPlayerInteract(PlayerInteractEvent event) {
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Block clicked = event.getClickedBlock();
			if (clicked.getType() != Material.STONE_BUTTON) return;

			Player player = event.getPlayer();

			if (player.isInsideVehicle()) return;

			if (this.isHigherStationButton(clicked)) {
				if (!player.hasPermission("homestation.use")) {
					player.sendMessage(dataStore.getMessage(Message.NoPermission));
					return;
				}

				// teleport:
				SoftBlockLocation currentSoftLocation = new SoftBlockLocation(clicked.getLocation().subtract(0, 1, 0));
				// is spawn station?
				if (spawnStations.contains(currentSoftLocation)) {
					// teleport to home:
					PlayerData playerData = dataStore.getPlayerData(player);
					SoftBlockLocation homeLoc = playerData.homeLocation;
					if (homeLoc == null) {
						player.sendMessage(dataStore.getMessage(Message.NoHomeStationSet));
						return;
					}
					Location location = homeLoc.getBukkitLocation();
					BlockFace stationFacing = location != null ? this.getStationFaceForLowerStationButton(location.getBlock()) : null;

					if (stationFacing == null) {
						player.sendMessage(dataStore.getMessage(Message.HomeStationNotFound));
						return;
					}

					this.teleport(player, player.getLocation(), location, stationFacing);
				} else {
					// teleport to spawn station:
					PlayerData playerData = dataStore.getPlayerData(player);
					SoftBlockLocation spawnLoc = playerData.spawnLocation;
					if (spawnLoc == null) {
						player.sendMessage(dataStore.getMessage(Message.NoSpawnStationSet));
						spawnLoc = mainSpawnStation;
						// only print this message once, then automatically set the players spawn station:
						playerData.spawnLocation = mainSpawnStation;
						if (spawnLoc == null) {
							player.sendMessage(dataStore.getMessage(Message.NoMainSpawnStationSet));
							return;
						}
					}
					Location location = spawnLoc.getBukkitLocation();
					BlockFace stationFacing = location != null ? this.getStationFaceForLowerStationButton(location.getBlock()) : null;

					if (stationFacing == null) {
						player.sendMessage(dataStore.getMessage(Message.SpawnStationNotFound));
						return;
					}

					this.teleport(player, player.getLocation(), location, stationFacing);
				}
			} else if (this.isLowerStationButton(clicked)) {
				if (!player.hasPermission("homestation.use")) {
					player.sendMessage(dataStore.getMessage(Message.NoPermission));
					return;
				}
				PlayerData playerData = dataStore.getPlayerData(player);
				SoftBlockLocation stationLocation = new SoftBlockLocation(clicked.getLocation());
				// is spawn station?
				if (spawnStations.contains(stationLocation)) {
					// set spawn station:
					playerData.spawnLocation = stationLocation;
					player.sendMessage(dataStore.getMessage(Message.SpawnStationSet));
				} else {
					// set home station:
					playerData.homeLocation = stationLocation;
					player.sendMessage(dataStore.getMessage(Message.HomeStationSet));
				}

				dataStore.savePlayerData(player.getUniqueId(), playerData);
			}
		}
	}

	public void playUpEffectAt(final Location location, final double end) {
		playerEffect1At(location);
		getServer().getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				playerEffect2At(location.add(0.0D, upEffectDistance, 0.0D));
				if (location.add(0.0D, upEffectDistance, 0.0D).getY() < end) {
					getServer().getScheduler().runTaskLater(HomeStations.instance, new Runnable() {

						@Override
						public void run() {
							playUpEffectAt(location, end);
						}
					}, 1L);
				}
			}
		}, 1L);
	}

	public void playDownEffectAt(final Location location, final double end) {
		playerEffect2At(location);
		getServer().getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				playerEffect1At(location.subtract(0, 1, 0));
				if (location.subtract(0, 1, 0).getY() >= end) {
					getServer().getScheduler().runTaskLater(HomeStations.instance, new Runnable() {

						@Override
						public void run() {
							playDownEffectAt(location, end);
						}
					}, 1L);
				}
			}
		}, 1L);
	}

	private void playerEffect1At(Location location) {
		try {
			fplayer.playFirework(location.getWorld(), location, fe1);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void playerEffect2At(Location location) {
		try {
			fplayer.playFirework(location.getWorld(), location, fe2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private float faceToYaw(final BlockFace face) {
		switch (face) {
		case WEST:
			return -270F;
		case NORTH:
			return -180F;
		case EAST:
			return -90F;
		case SOUTH:
			return 0F;
		default:
			return 0F;
		}
	}

	private float invert(float yaw) {
		return (yaw - 180) % 360;
	}

	private void teleport(final Player player, final Location from, final Location to, final BlockFace stationFacing) {
		// teleport into the middle of the block:
		to.add(toBlockMid);
		// rotate player facing the station:
		to.setYaw(invert(faceToYaw(stationFacing)));

		// teleport with some nice effect:
		playUpEffectAt(from, Math.min(from.getY() + maxUpEffectRange, from.getWorld().getMaxHeight()));
		player.setVelocity(upVelocity);
		// downwards effect
		final double endDownEffect = to.getY();
		final Location effectLocation = to.clone().add(0, downEffectOffset, 0);
		// offset:
		to.add(0, teleportYOffset, 0);

		getServer().getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				player.teleport(to);
				// effect height:
				playDownEffectAt(effectLocation, endDownEffect);
			}
		}, teleportDelay);

	}
}