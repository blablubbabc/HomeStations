/**
 * Copyright (c) blablubbabc <http://www.blablubbabc.de>
 * All rights reserved.
 */
package de.blablubbabc.homestations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

import de.blablubbabc.homestations.external.EconomyController;
import de.blablubbabc.homestations.utils.SoftBlockLocation;
import de.blablubbabc.homestations.utils.Utils;

public class HomeStations extends JavaPlugin implements Listener {

	private static HomeStations instance;

	public static HomeStations getInstance() {
		return instance;
	}

	public static final String PERMISSION_ADMIN = "homestation.admin";
	public static final String PERMISSION_USE = "homestation.use";

	private final Vector toBlockMid = new Vector(0.5, 0, 0.5);

	private DataStore dataStore;
	private YamlConfiguration homesConfig;
	private Set<SoftBlockLocation> spawnStations;
	private SoftBlockLocation mainSpawnStation;

	private FireworkEffect fe1;
	private FireworkEffect fe2;

	private Vector upVelocity;
	private long teleportDelay;

	private double maxUpEffectRange;
	private double upEffectDistance;

	private double downEffectOffset;
	private double teleportYOffset;

	private double teleportCosts;

	private final EconomyController economyController = new EconomyController();
	// playerUUID -> request
	private final Map<UUID, ConfirmationRequest> confirmationRequests = new HashMap<>();

	@Override
	public void onEnable() {
		instance = this;

		// load config:
		this.loadConfig();

		// save config (writing defaults):
		this.saveConfig();

		// initialize DataStore:
		dataStore = new DataStore(this.getLogger());

		// load spawn stations locations:
		homesConfig = YamlConfiguration.loadConfiguration(new File(DataStore.homesFilePath));
		spawnStations = new HashSet<>(SoftBlockLocation.getFromStringList(homesConfig.getStringList("Homes.Spawn Stations")));
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
						getLogger().warning("Invalid spawn station found (" + softLocation.toString() + "). Removing it now.");
						spawnLocs.remove();
					}
				}

				// load and validate spawn station:
				if (mainSpawnStation != null) {
					Location mainLocation = mainSpawnStation.getBukkitLocation();
					if (mainLocation == null || !isLowerStationButton(mainLocation.getBlock())) {
						getLogger().warning("Invalid main spawn station (" + mainSpawnStation.toString() + "). Removing it now.");
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

		// register listeners:
		Bukkit.getServer().getPluginManager().registerEvents(this, this);

		// economy controller:
		economyController.enable(this);

		// load data for all online players:
		for (Player player : Bukkit.getOnlinePlayers()) {
			// get his player data, forcing it to initialize if we've never seen him before
			dataStore.getPlayerData(player);
		}

		// reset confirmation requests, just in case:
		confirmationRequests.clear();
	}

	// loads the config and writes back loaded (defaults and corrected) values
	private void loadConfig() {
		// read config:
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
		maxUpEffectRange = config.getDouble("Upward.Effect.Maximum Range", 80.0D);
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

		// teleport costs:
		teleportCosts = config.getDouble("Teleport Costs", 0.0D);
		config.set("Teleport Costs", teleportCosts);
	}

	@Override
	public void onDisable() {
		// reset confirmation requests:
		confirmationRequests.clear();

		// economy controller:
		economyController.disable();

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
		List<Color> colors = new ArrayList<>();
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
		List<String> strings = new ArrayList<>();
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
			sender.sendMessage("This command can only be run as player.");
			return true;
		}

		Player player = (Player) sender;
		if (!player.hasPermission(PERMISSION_ADMIN)) {
			Utils.sendMessage(player, dataStore.getMessage(Message.NoPermission));
			return true;
		}

		if (args.length == 1) {
			Location location = player.getLocation();
			if (args[0].equalsIgnoreCase("setMainSpawn")) {
				if (!this.isLowerStationButton(location.getBlock())) {
					Utils.sendMessage(player, dataStore.getMessage(Message.ThisIsNoStation));
					return true;
				}
				mainSpawnStation = new SoftBlockLocation(location);
				// just in cases this wasn't already a spawn station before; set will make sure that it is only kept
				// once:
				spawnStations.add(mainSpawnStation);

				// cleanup affected confirmation requests:
				this.removeAffectedConfirmationRequests(mainSpawnStation);

				// message:
				Utils.sendMessage(player, dataStore.getMessage(Message.MainSpawnStationSet));

				// save:
				this.saveSpawnStations();
				return true;
			} else if (args[0].equalsIgnoreCase("addSpawn")) {
				if (!this.isLowerStationButton(location.getBlock())) {
					Utils.sendMessage(player, dataStore.getMessage(Message.ThisIsNoStation));
					return true;
				}
				SoftBlockLocation spawnStationLocation = new SoftBlockLocation(location);
				// set will make sure that it is only kept once:
				spawnStations.add(spawnStationLocation);

				// cleanup affected confirmation requests:
				this.removeAffectedConfirmationRequests(spawnStationLocation);

				// message:
				Utils.sendMessage(player, dataStore.getMessage(Message.SpawnStationAdded));

				// save:
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
			this.getLogger().severe("Unable to write to the configuration file at \"" + DataStore.homesFilePath + "\"");
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
		confirmationRequests.remove(playerId);
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
				if (!player.hasPermission(PERMISSION_USE)) {
					Utils.sendMessage(player, dataStore.getMessage(Message.NoPermission));
					return;
				}
				SoftBlockLocation currentStationLocation = new SoftBlockLocation(clicked.getLocation().subtract(0, 1, 0));

				// is spawn station?
				if (spawnStations.contains(currentStationLocation)) {
					// teleport to home:
					PlayerData playerData = dataStore.getPlayerData(player);
					SoftBlockLocation homeLoc = playerData.homeLocation;
					if (homeLoc == null) {
						Utils.sendMessage(player, dataStore.getMessage(Message.NoHomeStationSet));
						return;
					}
					Location location = homeLoc.getBukkitLocation();
					BlockFace stationFacing = location != null ? this.getStationFaceForLowerStationButton(location.getBlock()) : null;

					if (stationFacing == null) {
						Utils.sendMessage(player, dataStore.getMessage(Message.HomeStationNotFound));
						return;
					}

					// handle teleport costs:
					if (!this.handleTeleportCost(player, currentStationLocation)) {
						// failure
						return;
					}

					// teleport:
					Utils.sendMessage(player, dataStore.getMessage(Message.TeleportToHome));
					this.teleport(player, player.getLocation(), location, stationFacing);
				} else {
					// teleport to spawn station:
					PlayerData playerData = dataStore.getPlayerData(player);
					SoftBlockLocation spawnLoc = playerData.spawnLocation;
					if (spawnLoc == null) {
						Utils.sendMessage(player, dataStore.getMessage(Message.NoSpawnStationSet));
						spawnLoc = mainSpawnStation;
						// only print this message once, then automatically set the players spawn station:
						playerData.spawnLocation = mainSpawnStation;
						if (spawnLoc == null) {
							Utils.sendMessage(player, dataStore.getMessage(Message.NoMainSpawnStationSet));
							return;
						}
					}
					Location location = spawnLoc.getBukkitLocation();
					BlockFace stationFacing = location != null ? this.getStationFaceForLowerStationButton(location.getBlock()) : null;

					if (stationFacing == null) {
						Utils.sendMessage(player, dataStore.getMessage(Message.SpawnStationNotFound));
						return;
					}

					// handle teleport costs:
					if (!this.handleTeleportCost(player, currentStationLocation)) {
						// failure
						return;
					}

					// teleport:
					Utils.sendMessage(player, dataStore.getMessage(Message.TeleportToSpawn));
					this.teleport(player, player.getLocation(), location, stationFacing);
				}
			} else if (this.isLowerStationButton(clicked)) {
				if (!player.hasPermission(PERMISSION_USE)) {
					Utils.sendMessage(player, dataStore.getMessage(Message.NoPermission));
					return;
				}
				SoftBlockLocation currentStationLocation = new SoftBlockLocation(clicked.getLocation());
				PlayerData playerData = dataStore.getPlayerData(player);
				// get and remove last confirmation request:
				UUID playerId = player.getUniqueId();
				ConfirmationRequest confirmation = confirmationRequests.remove(playerId);

				// is spawn station?
				if (spawnStations.contains(currentStationLocation)) {
					// no confirmation required if message is empty:
					String confirmationMessage = dataStore.getMessage(Message.SpawnStationSetConfirm);
					if (!confirmationMessage.isEmpty() && (confirmation == null || !confirmation.applies(ConfirmationRequest.Type.SetStation, currentStationLocation))) {
						// request new confirmation:
						confirmationRequests.put(playerId, new ConfirmationRequest(ConfirmationRequest.Type.SetStation, currentStationLocation));
						Utils.sendMessage(player, confirmationMessage);
						return;
					}

					// set spawn station:
					playerData.spawnLocation = currentStationLocation;
					Utils.sendMessage(player, dataStore.getMessage(Message.SpawnStationSet));
				} else { // home station:
					// no confirmation required if message is empty:
					String confirmationMessage = dataStore.getMessage(Message.HomeStationSetConfirm);
					if (!confirmationMessage.isEmpty() && (confirmation == null || !confirmation.applies(ConfirmationRequest.Type.SetStation, currentStationLocation))) {
						// request new confirmation:
						confirmationRequests.put(playerId, new ConfirmationRequest(ConfirmationRequest.Type.SetStation, currentStationLocation));
						Utils.sendMessage(player, confirmationMessage);
						return;
					}

					// set home station:
					playerData.homeLocation = currentStationLocation;
					Utils.sendMessage(player, dataStore.getMessage(Message.HomeStationSet));
				}

				dataStore.savePlayerData(player.getUniqueId(), playerData);
			}
		}
	}

	private boolean handleTeleportCost(Player player, SoftBlockLocation currentStationLocation) {
		// handle teleport costs:
		if (teleportCosts != 0.0D && economyController.hasEconomy()) {
			UUID playerId = player.getUniqueId();

			// get and remove last confirmation request:
			ConfirmationRequest confirmation = confirmationRequests.remove(playerId);

			// check balance:
			double balance = economyController.getBalance(player);
			if (teleportCosts > 0.0D && balance < teleportCosts) {
				// not enough money:
				Utils.sendMessage(player, dataStore.getMessage(Message.NotEnoughMoney,
						"costs", economyController.formatBalance(Math.abs(teleportCosts)),
						"balance", economyController.formatBalance(balance)));
				return false;
			}

			// no confirmation required if message is empty:
			String confirmationMessage = dataStore.getMessage(Message.TeleportCostsConfirm,
					"costs", economyController.formatBalance(Math.abs(teleportCosts)),
					"balance", economyController.formatBalance(balance));
			if (!confirmationMessage.isEmpty() && (confirmation == null || !confirmation.applies(ConfirmationRequest.Type.TeleportCost, currentStationLocation))) {
				// request new confirmation:
				confirmationRequests.put(playerId, new ConfirmationRequest(ConfirmationRequest.Type.TeleportCost, currentStationLocation));
				Utils.sendMessage(player, confirmationMessage);
				return false;
			} else {
				// update balance:
				String error = economyController.applyChange(player, -teleportCosts, false);
				if (error != null) {
					// transaction failure:
					Utils.sendMessage(player, dataStore.getMessage(Message.TransactionFailure,
							"error", error));
					return false;
				} else {
					// transaction successful:
					balance = economyController.getBalance(player); // new balance
					Utils.sendMessage(player, dataStore.getMessage(Message.TeleportCostsApplied,
							"costs", economyController.formatBalance(Math.abs(teleportCosts)),
							"balance", economyController.formatBalance(balance)));
				}
			}
		}

		// proceed with teleport..
		return true;
	}

	public void playUpEffectAt(final Location location, final double end) {
		playEffect1At(location);
		getServer().getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				playEffect2At(location.add(0.0D, upEffectDistance, 0.0D));
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
		playEffect2At(location);
		getServer().getScheduler().runTaskLater(this, new Runnable() {

			@Override
			public void run() {
				playEffect1At(location.subtract(0, 1, 0));
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

	private void playEffect1At(Location location) {
		Utils.playFireworkEffect(location, fe1);
	}

	private void playEffect2At(Location location) {
		Utils.playFireworkEffect(location, fe2);
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

	public void removeAffectedConfirmationRequests(SoftBlockLocation affectedStationLocation) {
		assert affectedStationLocation != null;
		Iterator<ConfirmationRequest> confirmations = confirmationRequests.values().iterator();
		while (confirmations.hasNext()) {
			ConfirmationRequest confirmation = confirmations.next();
			if (affectedStationLocation.equals(confirmation.getAffectedStationLocation())) {
				confirmations.remove();
			}
		}
	}

	public static class ConfirmationRequest {

		public static final int DEFAULT_DURATION_SECONDS = 20;

		public enum Type {
			TeleportCost,
			SetStation;
		}

		private final Type type;
		private final SoftBlockLocation affectedStationLocation;
		private final long expiration;

		public ConfirmationRequest(Type type, SoftBlockLocation affectedStationLocation) {
			this(type, affectedStationLocation, DEFAULT_DURATION_SECONDS);
		}

		public ConfirmationRequest(Type type, SoftBlockLocation affectedStationLocation, int durationInSeconds) {
			this.type = type;
			this.affectedStationLocation = affectedStationLocation;
			this.expiration = System.currentTimeMillis() + durationInSeconds * 1000L;
		}

		public Type getType() {
			return type;
		}

		public SoftBlockLocation getAffectedStationLocation() {
			return affectedStationLocation;
		}

		public boolean isExpired() {
			// Note: Depending on changes to the local system time, the confirmation request might not expire in time,
			// or expire instantly. However, the expiration state is not that important that changes to the system time
			// would need to be handled.
			return expiration < System.currentTimeMillis();
		}

		public boolean matches(Type type, SoftBlockLocation affectedStationLocation) {
			return this.type.equals(type) && this.affectedStationLocation.equals(affectedStationLocation);
		}

		// matches and is not expired
		public boolean applies(Type type, SoftBlockLocation affectedStationLocation) {
			return this.matches(type, affectedStationLocation) && !this.isExpired();
		}
	}
}
