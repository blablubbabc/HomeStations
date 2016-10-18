/**
 * Copyright (c) blablubbabc <http://www.blablubbabc.de>
 * All rights reserved.
 */
package de.blablubbabc.homestations.external;

import java.text.DecimalFormat;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;

public class VaultController {

	private VaultController() {
	}

	private static class PluginListener implements Listener {

		private PluginListener() {
		}

		@EventHandler
		public void onServiceRegister(ServiceRegisterEvent event) {
			if (!findVaultEconomy()) return;

			RegisteredServiceProvider<?> serviceProvider = event.getProvider();
			if (serviceProvider.getService() == Economy.class) {
				// update:
				setupEconomy();
			}
		}

		@EventHandler
		public void onServiceUnregister(ServiceUnregisterEvent event) {
			if (!findVaultEconomy()) return;

			RegisteredServiceProvider<?> serviceProvider = event.getProvider();
			if (serviceProvider.getService() == Economy.class) {
				// update:
				setupEconomy();
			}
		}
	}

	public static final String PLUGIN_NAME = "Vault";
	public static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0#");

	private static final PluginListener pluginListener = new PluginListener();
	private static Plugin plugin = null; // the main plugin

	public static boolean isEnabled() {
		return plugin != null;
	}

	public static void enable(Plugin mainPlugin) {
		if (mainPlugin == null || !mainPlugin.isEnabled()) {
			throw new IllegalArgumentException("Plugin is not enabled!");
		}
		if (isEnabled()) {
			// disable first:
			disable();
		}
		mainPlugin.getLogger().info("Enabling controller for plugin '" + PLUGIN_NAME + "'.");

		// enable:
		plugin = mainPlugin;
		plugin.getServer().getPluginManager().registerEvents(pluginListener, plugin);

		// setup:
		setup();
	}

	public static void disable() {
		if (!isEnabled()) return;
		plugin.getLogger().info("Disabling controller for plugin '" + PLUGIN_NAME + "'");

		// cleanup:
		cleanup();

		// disable:
		HandlerList.unregisterAll(pluginListener);
		plugin = null;
	}

	// ////////

	public static Plugin getPlugin() {
		return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
	}

	public static boolean isPluginEnabled() {
		Plugin plugin = getPlugin();
		return plugin != null && plugin.isEnabled();
	}

	private static void setup() {
		// setup economy:
		setupEconomy();
	}

	private static void cleanup() {
		cleanupEconomy();
	}

	// ECONOMY:

	private static Economy economy = null;

	private static boolean findVaultEconomy() {
		try {
			Class.forName("net.milkbowl.vault.economy.Economy");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static void setupEconomy() {
		// cleanup:
		cleanupEconomy();

		// check if vault economy is available:
		if (!findVaultEconomy()) {
			plugin.getLogger().info("Could not find vault economy. Additional vault economy features disabled.");
			return;
		}

		// setup economy:
		RegisteredServiceProvider<Economy> economyService = Bukkit.getServicesManager().getRegistration(Economy.class);
		if (economyService != null) {
			economy = economyService.getProvider();
		}
		if (economy == null) {
			plugin.getLogger().info("No economy service detected. Additional vault economy features disabled.");
		} else {
			plugin.getLogger().info("Found economy service: " + economy.getName());
		}
	}

	private static void cleanupEconomy() {
		economy = null;
	}

	// ///

	public static boolean hasEconomy() {
		return economy != null;
	}

	private static void validateHasEconomy() {
		if (!hasEconomy()) {
			throw new IllegalArgumentException("No economy available!");
		}
	}

	/**
	 * Deposits or withdraws the specified amount of money in/from the player's account.
	 * 
	 * <p>
	 * This requires {@link #hasEconomy()} to return <code>true</code> in order to work.
	 * </p>
	 * 
	 * @param player
	 *            the player
	 * @param deltaAmount
	 *            the money changes
	 * @param withdrawPartial
	 *            whether to withdraw partial amount if the player's balance isn't large enough
	 * @return an error message, or <code>null</code> on success
	 */
	public static String applyChange(OfflinePlayer player, double deltaAmount, boolean withdrawPartial) {
		if (deltaAmount == 0.0D) return null;
		if (deltaAmount > 0.0D) {
			return depositMoney(player, deltaAmount);
		} else {
			deltaAmount = -deltaAmount;
			double balance = getBalance(player);
			if (balance < deltaAmount) {
				if (withdrawPartial) deltaAmount = balance;
				else return "Not enough money.";
			}
			return withdrawMoney(player, deltaAmount);
		}
	}

	/**
	 * Deposits the specified amount of money in the player's account.
	 * 
	 * <p>
	 * This requires {@link #hasEconomy()} to return <code>true</code> in order to work.
	 * </p>
	 * 
	 * @param player
	 *            the player
	 * @param addAmount
	 *            the amount of money, has to be positive
	 * @return an error message, or <code>null</code> on success
	 */
	public static String depositMoney(OfflinePlayer player, double addAmount) {
		validateHasEconomy();
		if (player == null) {
			throw new IllegalArgumentException("Player is null!");
		}
		if (addAmount <= 0.0D) return "Cannot deposit a negative amount.";

		EconomyResponse response = economy.depositPlayer(player, addAmount);
		return getErrorMessage(response);
	}

	/**
	 * Withdraws the specified amount of money from the player's account.
	 * 
	 * <p>
	 * This requires {@link #hasEconomy()} to return <code>true</code> in order to work.
	 * </p>
	 * 
	 * @param player
	 *            the player
	 * @param withdrawAmount
	 *            the amount of money, has to be positive
	 * @return an error message, or <code>null</code> on success
	 */
	public static String withdrawMoney(OfflinePlayer player, double withdrawAmount) {
		validateHasEconomy();
		if (player == null) {
			throw new IllegalArgumentException("Player is null!");
		}
		if (withdrawAmount <= 0.0D) return "Cannot withdraw a negative amount.";

		EconomyResponse response = economy.withdrawPlayer(player, withdrawAmount);
		return getErrorMessage(response);
	}

	private static String getErrorMessage(EconomyResponse response) {
		if (response.transactionSuccess()) {
			return null;
		} else {
			String errorMessage = response.errorMessage;
			if (errorMessage == null) {
				// construct an error message if none is provided:
				if (response.type == ResponseType.NOT_IMPLEMENTED) {
					errorMessage = "This operation is not supported by the economy plugin.";
				} else {
					errorMessage = "Unknown issue.";
				}
			}
			return errorMessage;
		}
	}

	/**
	 * Gets the player's balance.
	 * 
	 * <p>
	 * This requires {@link #hasEconomy()} to return <code>true</code> in order to work.
	 * </p>
	 * 
	 * @param player
	 *            the player
	 * @return the amount of money the player currently has
	 */
	public static double getBalance(OfflinePlayer player) {
		validateHasEconomy();
		if (player == null) {
			throw new IllegalArgumentException("Player is null!");
		}

		return economy.getBalance(player);
	}
}
