package de.blablubbabc.homestations;

import java.io.*;
import java.util.*;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import de.blablubbabc.homestations.utils.Log;
import de.blablubbabc.homestations.utils.SoftBlockLocation;

class DataStore {
	// in-memory cache for player data
	private HashMap<String, PlayerData> playerNameToPlayerDataMap = new HashMap<String, PlayerData>();
	
	// in-memory cache for messages
	private String [] messages;
	
	// path information:
	final static String dataLayerFolderPath = "plugins" + File.separator + "HomeStations";
	final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
	final static String homesFilePath = dataLayerFolderPath + File.separator + "homes.yml";
	final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
	
	DataStore() {
		// load up all the messages from messages.yml
		this.loadMessages();
		
		// ensure data folders exist
		new File(playerDataFolderPath).mkdirs();
	}
	
	// removes cached player data from memory
	void clearCachedPlayerData(String playerName) {
		this.playerNameToPlayerDataMap.remove(playerName);
	}
	
	// retrieves player data from memory or file, as necessary
	// if the player has never been on the server before, this will return a fresh player data with default values
	public PlayerData getPlayerData(String playerName) {
		// first, look in memory
		PlayerData playerData = this.playerNameToPlayerDataMap.get(playerName);
		
		// if not there, look on disk
		if (playerData == null)
		{
			playerData = this.loadPlayerDataFromStorage(playerName);
			
			// store that new player data into the hash map cache
			this.playerNameToPlayerDataMap.put(playerName, playerData);
		}
		
		// try the hash map again.  if it's STILL not there, we have a bug to fix
		return this.playerNameToPlayerDataMap.get(playerName);
	}
	
	// returns PlayerData for a player with the given name and RETURNS NULL if no PlayerData was found for this name. 
	// The loaded playerData will not be saved in memory and is meant for one-time lookup purposes.
	public PlayerData getPlayerDataIfExist(String playerName) {
		// first, look in memory
		PlayerData playerData = this.playerNameToPlayerDataMap.get(playerName);

		// if not there, look on disk
		if (playerData == null) {
			playerData = this.loadPlayerDataFromStorageIfExist(playerName);
		}
		
		return playerData;
	}
	
	PlayerData loadPlayerDataFromStorage(String playerName) {
		PlayerData playerData = loadPlayerDataFromStorageIfExist(playerName);
		
		// if it doesn't exist, init default:
		if (playerData == null) {
			playerData = new PlayerData();
		}
		
		return playerData;
	}
	
	// loading PlayerData by a given name and returns null, if there is no data stored for a player with this name
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
				if(inStream != null) inStream.close();
			}
			catch(IOException exception) {}
		}
		
		return playerData;
	}
	
	// saves changes to player data.  MUST be called after you're done making changes, otherwise a reload will lose them
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
		} catch(IOException exception) {}
	}
	
	// whether or not data was stored for a player with this name
	boolean existsPlayerData(String playerName) {
		File playerFile = new File(playerDataFolderPath + File.separator + playerName);
		//whether or not the file exists
		return playerFile.exists();
	}
	
	// loads user-facing messages from the messages.yml configuration file into memory
	private void loadMessages() {
		Message[] messageIDs = Message.values();
		this.messages = new String[Message.values().length];
		
		Map<String, CustomizableMessage> defaults = new HashMap<String, CustomizableMessage>();
		
		// initialize defaults
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
		
		//load the message config file
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));
		
		// for each message ID
		for (int i = 0; i < messageIDs.length; i++) {
			// get default for this message
			Message messageID = messageIDs[i];
			CustomizableMessage messageData = defaults.get(messageID.name());
			
			// if default is missing, log an error and use some fake data for now so that the plugin can run
			if (messageData == null) {
				Log.severe("Missing message for " + messageID.name() + ".  Please contact the developer.");
				messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
			}
			
			// read the message from the file, use default if necessary
			String message = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
			config.set("Messages." + messageID.name() + ".Text", message);
			
			// colorize and store message
			this.messages[messageID.ordinal()] = ChatColor.translateAlternateColorCodes('&', message);
			
			if (messageData.notes != null) {
				messageData.notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
				config.set("Messages." + messageID.name() + ".Notes", messageData.notes);
			}
		}
		
		//save any changes
		try {
			config.save(DataStore.messagesFilePath);
		} catch(IOException exception) {
			Log.severe("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
		}
		
		defaults.clear();
		System.gc();				
	}

	// helper for above, adds a default message and notes to go with a message ID
	private void addDefault(Map<String, CustomizableMessage> defaults, Message id, String text, String notes) {
		CustomizableMessage message = new CustomizableMessage(id, text, notes);
		defaults.put(id.name(), message);		
	}

	// gets a message from memory
	public String getMessage(Message messageID, String... args) {
		String message = messages[messageID.ordinal()];
		
		for (int i = 0; i < args.length; i++) {
			String param = args[i];
			message = message.replace("{" + i + "}", param);
		}
		
		return message;
		
	}
	
}