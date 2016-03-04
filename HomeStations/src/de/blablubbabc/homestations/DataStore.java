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

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import de.blablubbabc.homestations.utils.Log;
import de.blablubbabc.homestations.utils.SoftBlockLocation;

class DataStore {

	// path information:
	final static String pluginFolderPath = "plugins" + File.separator + "HomeStations";
	final static String messagesFilePath = pluginFolderPath + File.separator + "messages.yml";
	final static String homesFilePath = pluginFolderPath + File.separator + "homes.yml";
	final static String playerDataFolderPath = pluginFolderPath + File.separator + "PlayerData";

	// in-memory cache for player data:
	private final Map<String, PlayerData> playerNameToPlayerDataMap = new HashMap<String, PlayerData>();

	// in-memory cache for messages:
	private final Map<Message, String> messages = new EnumMap<Message, String>(Message.class);

	DataStore() {
		// load messages:
		this.loadMessages();

		// ensure player data folders exist:
		new File(playerDataFolderPath).mkdirs();
	}

	// removes cached player data from memory
	@Deprecated
	void clearCachedPlayerData(String playerName) {
		playerNameToPlayerDataMap.remove(playerName);
	}

	// retrieves player data from memory or file, as necessary
	// if the player has never been on the server before, this will return a fresh player data with default values
	@Deprecated
	public PlayerData getPlayerData(String playerName) {
		// first, look in memory
		PlayerData playerData = playerNameToPlayerDataMap.get(playerName);

		// if not there, look on disk
		if (playerData == null)
		{
			playerData = this.loadPlayerDataFromStorage(playerName);

			// store that new player data into the hash map cache
			playerNameToPlayerDataMap.put(playerName, playerData);
		}

		// try the hash map again. if it's STILL not there, we have a bug to fix
		return playerNameToPlayerDataMap.get(playerName);
	}

	// returns PlayerData for a player with the given name and RETURNS NULL if no PlayerData was found for this name.
	// The loaded playerData will not be saved in memory and is meant for one-time lookup purposes.
	@Deprecated
	public PlayerData getPlayerDataIfExist(String playerName) {
		// first, look in memory
		PlayerData playerData = playerNameToPlayerDataMap.get(playerName);

		// if not there, look on disk
		if (playerData == null) {
			playerData = this.loadPlayerDataFromStorageIfExist(playerName);
		}

		return playerData;
	}

	@Deprecated
	PlayerData loadPlayerDataFromStorage(String playerName) {
		PlayerData playerData = loadPlayerDataFromStorageIfExist(playerName);

		// if it doesn't exist, init default:
		if (playerData == null) {
			playerData = new PlayerData();
		}

		return playerData;
	}

	// loading PlayerData by a given name and returns null, if there is no data stored for a player with this name
	@Deprecated
	PlayerData loadPlayerDataFromStorageIfExist(String playerName) {
		File playerFile = new File(playerDataFolderPath + File.separator + playerName);

		PlayerData playerData = new PlayerData();

		// if it doesn't exist as a file
		if (!playerFile.exists()) {
			return null;
		} else {
			// otherwise, read the file
			BufferedReader inStream = null;
			try {
				inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));

				// first line is the location as string
				String homeLocationString = inStream.readLine();
				// first line is the location as string
				String spawnLocationString = inStream.readLine();

				// convert those to SoftBlockLocations and store them
				playerData.homeLocation = SoftBlockLocation.getFromString(homeLocationString);
				playerData.spawnLocation = SoftBlockLocation.getFromString(spawnLocationString);

				inStream.close();
			}

			// if there's any problem with the file's content, log an error message
			catch (Exception e) {
				Log.severe("Unable to load data for player \"" + playerName + "\": " + e.getMessage());
			}

			try {
				if (inStream != null) inStream.close();
			} catch (IOException exception) {
			}
		}

		return playerData;
	}

	// saves changes to player data. MUST be called after you're done making changes, otherwise a reload will lose them
	@Deprecated
	void savePlayerData(String playerName, PlayerData playerData) {
		BufferedWriter outStream = null;
		try {
			// open the player's file
			File playerDataFile = new File(playerDataFolderPath + File.separator + playerName);
			playerDataFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(playerDataFile));

			// first line is the home location:
			outStream.write(playerData.homeLocation != null ? playerData.homeLocation.toString() : "not set");
			outStream.newLine();
			// second line is the spawn location:
			outStream.write(playerData.spawnLocation != null ? playerData.spawnLocation.toString() : "not set");
			outStream.newLine();

		} catch (Exception e) {
			// if any problem, log it
			Log.severe("Unexpected exception saving data for player \"" + playerName + "\": " + e.getMessage());
		}

		try {
			// close the file
			if (outStream != null) {
				outStream.close();
			}
		} catch (IOException exception) {
		}
	}

	// whether or not data was stored for a player with this name
	@Deprecated
	boolean existsPlayerData(String playerName) {
		File playerFile = new File(playerDataFolderPath + File.separator + playerName);
		// whether or not the file exists
		return playerFile.exists();
	}

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