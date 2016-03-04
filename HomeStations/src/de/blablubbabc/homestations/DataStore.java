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

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import de.blablubbabc.homestations.utils.Log;
import de.blablubbabc.homestations.utils.SoftBlockLocation;

class DataStore {

	// path information:
	final static String pluginFolderPath = "plugins" + File.separator + "HomeStations";
	final static String messagesFilePath = pluginFolderPath + File.separator + "messages.yml";
	final static String homesFilePath = pluginFolderPath + File.separator + "homes.yml";
	final static String playerDataFolderPath = pluginFolderPath + File.separator + "PlayerData";

	// in-memory cache for player data:
	private final Map<UUID, PlayerData> playerIdToPlayerDataMap = new HashMap<UUID, PlayerData>();

	// in-memory cache for messages:
	private final Map<Message, String> messages = new EnumMap<Message, String>(Message.class);

	DataStore() {
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
			Log.severe("Unable to load player data from \"" + playerFile.getPath() + "\": " + e.getMessage());
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
			Log.severe("Unexpected exception saving data for player \"" + playerId + "\": " + e.getMessage());
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
		Map<Message, MessageData> defaults = new EnumMap<Message, MessageData>(Message.class);
		this.addDefault(defaults, Message.SpawnStationAdded, "&aA spawn station was added!", null);
		this.addDefault(defaults, Message.MainSpawnStationSet, "&aThe main spawn station was set!", null);
		this.addDefault(defaults, Message.NoMainSpawnStationSet, "&cThere is no valid main spawn station set yet!", null);
		this.addDefault(defaults, Message.HomeStationSet, "&aYou have set your home station! You will teleport here every time you trigger the top button of a spawn station.", null);
		this.addDefault(defaults, Message.NoHomeStationSet, "&cYou don't have a home station set yet!", null);
		this.addDefault(defaults, Message.HomeStationNotFound, "&cYour home station does no longer exist!", null);
		this.addDefault(defaults, Message.SpawnStationSet, "&aYou have set your spawn station! You will teleport here every time you trigger the top button of a home station.", null);
		this.addDefault(defaults, Message.NoSpawnStationSet, "&cYou don't have a spawn station set yet! &6Selecting main spawn station now.", null);
		this.addDefault(defaults, Message.SpawnStationNotFound, "&cYour spawn station does no longer exist!", null);
		this.addDefault(defaults, Message.ThisIsNoStation, "&cThis is not a valid station!", null);
		this.addDefault(defaults, Message.NoPermission, "&cYou don't have the permission to do that!", null);

		// load the message config file:
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));

		// load messages:
		for (Message messageID : Message.values()) {
			// get default for this message:
			MessageData messageData = defaults.get(messageID);

			// if default is missing, log an error and use some fake data for now so that the plugin can run:
			if (messageData == null) {
				Log.severe("Missing default message for '" + messageID.name() + "'.  Please contact the developer.");
				messageData = new MessageData(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
			}

			// read the message from the file, use default if necessary:
			String message = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
			// write value back to config (creates defaults):
			config.set("Messages." + messageID.name() + ".Text", message);

			// colorize and store message:
			messages.put(messageID, ChatColor.translateAlternateColorCodes('&', message));

			// write notes back to config (creates defaults):
			if (messageData.notes != null) {
				String notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
				config.set("Messages." + messageID.name() + ".Notes", notes);
			}
		}

		// save any changes
		try {
			config.save(DataStore.messagesFilePath);
		} catch (IOException exception) {
			Log.severe("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}

		defaults.clear();
		System.gc();
	}

	// helper for above, adds a default message and notes to go with a message ID
	private void addDefault(Map<Message, MessageData> defaults, Message id, String text, String notes) {
		MessageData message = new MessageData(id, text, notes);
		defaults.put(id, message);
	}

	// gets a message from memory
	public String getMessage(Message messageID, String... args) {
		String message = messages.get(messageID);
		if (message == null) message = "ERROR:Missing Message for '" + messageID + "'";

		// replace placeholders:
		if (args != null) {
			for (int i = 0; i < args.length; i++) {
				String param = args[i];
				message = message.replace("{" + i + "}", param);
			}
		}
		return message;
	}
}