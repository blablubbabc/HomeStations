/**
 * Copyright (c) blablubbabc <http://www.blablubbabc.de>
 * All rights reserved.
 */
package de.blablubbabc.homestations;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import de.blablubbabc.homestations.utils.SoftBlockLocation;
import de.blablubbabc.homestations.utils.Utils;

class DataStore {

	// path information:
	final static String pluginFolderPath = "plugins" + File.separator + "HomeStations";
	final static String messagesFilePath = pluginFolderPath + File.separator + "messages.yml";
	final static String homesFilePath = pluginFolderPath + File.separator + "homes.yml";
	final static String playerDataFolderPath = pluginFolderPath + File.separator + "PlayerData";

	private final Logger logger;

	// in-memory cache for player data:
	private final Map<UUID, PlayerData> playerIdToPlayerDataMap = new HashMap<>();

	// in-memory cache for messages:
	private final Map<Message, String> messages = new EnumMap<>(Message.class);

	DataStore(Logger logger) {
		this.logger = logger;

		// load messages:
		this.loadMessages();

		// ensure player data folders exist:
		new File(playerDataFolderPath).mkdirs();
	}

	/**
	 * Removes cached {@link PlayerData} from memory.
	 * 
	 * @param playerId
	 *            the player's unique id
	 */
	void clearCachedPlayerData(UUID playerId) {
		playerIdToPlayerDataMap.remove(playerId);
	}

	/**
	 * Retrieves {@link PlayerData} from memory or file, as necessary.
	 * 
	 * <p>
	 * If the player has never been on the server before, this will return a fresh {@link PlayerData} with default
	 * values.
	 * </p>
	 * 
	 * @param player
	 *            the player
	 * @return the player data
	 */
	public PlayerData getPlayerData(Player player) {
		UUID playerId = player.getUniqueId();
		// look in memory:
		PlayerData playerData = playerIdToPlayerDataMap.get(playerId);

		// if not there, look on disk and create default if it doesn't exist there either:
		if (playerData == null) {
			playerData = this.loadPlayerData(player);

			// store the new player data in the cache:
			playerIdToPlayerDataMap.put(playerId, playerData);
		}
		return playerData;
	}

	/**
	 * Loads the player data for the given player.
	 * 
	 * <p>
	 * If no player data exists yet, this will try to import the player data from the old player data file.<br>
	 * If the player has never been on the server before, this will return a fresh {@link PlayerData} with default
	 * values.
	 * </p>
	 * 
	 * @param player
	 *            the player
	 * @return the player data
	 */
	private PlayerData loadPlayerData(Player player) {
		UUID playerId = player.getUniqueId();
		// load player data from disk:
		PlayerData playerData = this.loadPlayerDataIfExist(playerId);

		if (playerData == null) {
			// import old player data if found:
			String playerName = player.getName();
			playerData = this.loadOldPlayerData(playerName);

			if (playerData != null) {
				// save imported player data:
				this.savePlayerData(playerId, playerData);

				// delete old player data file:
				this.getOldPlayerDataFile(playerName).delete();
			} else {
				// create fresh default player data:
				playerData = new PlayerData();
			}
		}
		return playerData;
	}

	/**
	 * Gets the {@link PlayerData} for a player with the given id.
	 * 
	 * <p>
	 * Returns <code>null</code> if no {@link PlayerData} was found for the given player id.<br>
	 * The loaded {@link PlayerData} will not be saved in memory and is meant for one-time lookup purposes.
	 * 
	 * @param playerId
	 *            the player id
	 * @return the player data, possibly <code>null</code>
	 */
	public PlayerData getPlayerDataIfExist(UUID playerId) {
		// look in memory:
		PlayerData playerData = playerIdToPlayerDataMap.get(playerId);

		// if not there, look on disk:
		if (playerData == null) {
			playerData = this.loadPlayerDataIfExist(playerId);
		}

		return playerData;
	}

	/**
	 * Loads the {@link PlayerData} for the given player id.
	 * 
	 * <p>
	 * Returns <code>null</code> if there is no data stored for the given player id.
	 * </p>
	 * 
	 * @param playerId
	 *            the player id
	 * @return the player data, possibly <code>null</code>
	 */
	PlayerData loadPlayerDataIfExist(UUID playerId) {
		File playerFile = new File(playerDataFolderPath, playerId.toString());
		return this.loadPlayerDataIfExist(playerFile);
	}

	// loads the player data from the given file:
	private PlayerData loadPlayerDataIfExist(File playerFile) {
		// check if player data file exists:
		if (!playerFile.exists()) {
			return null;
		}

		PlayerData playerData = new PlayerData();

		// read the file:
		BufferedReader inStream = null;
		try {
			inStream = new BufferedReader(new FileReader(playerFile));

			// first line is the home location as string:
			String homeLocationString = inStream.readLine();
			// second line is the spawn location as string:
			String spawnLocationString = inStream.readLine();

			// convert those to SoftBlockLocations and store them:
			if (homeLocationString != null) {
				playerData.homeLocation = SoftBlockLocation.getFromString(homeLocationString);
			}
			if (spawnLocationString != null) {
				playerData.spawnLocation = SoftBlockLocation.getFromString(spawnLocationString);
			}
		} catch (Exception e) {
			// log if a problem occurs:
			logger.severe("Unable to load player data from \"" + playerFile.getPath() + "\": " + e.getMessage());
		} finally {
			if (inStream != null) {
				try {
					inStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return playerData;
	}

	/**
	 * Saves the {@link PlayerData}. MUST be called after making changes, otherwise a reload will lose them.
	 * 
	 * @param playerId
	 *            the player's unique id
	 * @param playerData
	 *            the player data
	 */
	void savePlayerData(UUID playerId, PlayerData playerData) {
		BufferedWriter outStream = null;
		try {
			// open the player's file
			File playerDataFile = new File(playerDataFolderPath, playerId.toString());
			playerDataFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(playerDataFile));

			// first line is the home location:
			outStream.write(playerData.homeLocation != null ? playerData.homeLocation.toString() : "not set");
			outStream.newLine();
			// second line is the spawn location:
			outStream.write(playerData.spawnLocation != null ? playerData.spawnLocation.toString() : "not set");
			outStream.newLine();
		} catch (Exception e) {
			// log if a problem occurs:
			logger.severe("Unexpected exception saving data for player \"" + playerId + "\": " + e.getMessage());
		} finally {
			// close the file:
			if (outStream != null) {
				try {
					outStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Checks if there is data stored for a given player id.
	 * 
	 * @param playerId
	 *            the player id
	 * @return <code>true</code> if there is data stored, <code>false</code> otherwise
	 */
	boolean existsPlayerData(UUID playerId) {
		File playerFile = new File(playerDataFolderPath, playerId.toString());
		// whether or not the file exists:
		return playerFile.exists();
	}

	// HANDLING OF OLD PLAYER DATA

	private PlayerData loadOldPlayerData(String playerName) {
		// look for old player data:
		File oldPlayerDataFile = this.getOldPlayerDataFile(playerName);
		return this.loadPlayerDataIfExist(oldPlayerDataFile);
	}

	private File getOldPlayerDataFile(String playerName) {
		return new File(playerDataFolderPath, playerName);
	}

	// MESSAGES

	// loads user-facing messages from the messages.yml configuration file into memory
	private void loadMessages() {
		// initialize defaults:
		Map<Message, String> defaults = new EnumMap<>(Message.class);
		defaults.put(Message.SpawnStationAdded, "&aA &espawn station &awas added!");
		defaults.put(Message.MainSpawnStationSet, "&aThe &emain spawn station &awas set!");
		defaults.put(Message.NoMainSpawnStationSet, "&cThere is no valid &emain spawn station &cset yet!");
		defaults.put(Message.HomeStationSet, "&aYou have set your &ehome station&a!\\n&aYou will teleport here every time you trigger the top button of a &espawn station&a.");
		defaults.put(Message.NoHomeStationSet, "&cYou don't have a &ehome station &cset yet!");
		defaults.put(Message.HomeStationNotFound, "&cYour &ehome station &cdoes no longer exist!");
		defaults.put(Message.SpawnStationSet, "&aYou have set your &espawn station&a!\\n&aYou will teleport here every time you trigger the top button of a &ehome station&a.");
		defaults.put(Message.NoSpawnStationSet, "&cYou don't have a &espawn station &cset yet! &6Selecting the &emain spawn station &6for you.");
		defaults.put(Message.SpawnStationNotFound, "&cYour &espawn station &cdoes no longer exist!");
		defaults.put(Message.ThisIsNoStation, "&cThis is not a valid station!");
		defaults.put(Message.TeleportToHome, "&aTeleporting home...");
		defaults.put(Message.TeleportToSpawn, "&aTeleporting to spawn...");
		defaults.put(Message.NotEnoughMoney, "&cYou don't have enough money! Teleporting costs &e{costs}$&c, but you only have &e{balance}$&c.");
		defaults.put(Message.TransactionFailure, "&cSomething went wrong: &e{error}");
		defaults.put(Message.TeleportCostsConfirm, "&cTeleporting costs &e{costs}$&c, you have &e{balance}$&c! &6Click again to confirm.");
		defaults.put(Message.TeleportCostsApplied, "&aWithdrawn teleport costs of &e{costs}$&a. You have &e{balance}$ &aleft.");
		defaults.put(Message.NoPermission, "&cYou don't have the permission to do that!");

		// load the message config file:
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));

		// load messages:
		for (Message messageID : Message.values()) {
			// get default for this message:
			String defaultMessage = defaults.get(messageID);

			// if default is missing, log an error and use some fake data for now so that the plugin can run:
			if (defaultMessage == null) {
				logger.severe("Missing default message for '" + messageID.name() + "'.  Please contact the developer.");
				defaultMessage = "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.";
			}

			// read the message from the file, use default if necessary:
			String message = config.getString(messageID.name(), defaultMessage);
			// check for old legacy messages:
			String legacyKey = "Messages." + messageID.name() + ".Text";
			if (config.isSet(legacyKey)) {
				// use old legacy value instead:
				message = config.getString(legacyKey, defaultMessage);
			}

			// write value back to config (creates defaults):
			config.set(messageID.name(), message);

			// colorize and store message:
			messages.put(messageID, ChatColor.translateAlternateColorCodes('&', message));
		}

		// remove legacy values from config:
		config.set("Messages", null);

		// save any changes:
		try {
			config.save(DataStore.messagesFilePath);
		} catch (IOException exception) {
			logger.severe("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}
	}

	// gets a message from memory
	public String getMessage(Message messageID, String... placeholders) {
		String message = messages.get(messageID);
		if (message == null) message = "ERROR:Missing message for '" + messageID + "'";

		// replace placeholders:
		message = Utils.replacePlaceholders(message, placeholders);

		return message;
	}
}
