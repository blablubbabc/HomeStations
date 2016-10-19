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
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;

public class EconomyController {

	private class PluginListener implements Listener {

		private PluginListener() {
		}

		// in case the controller wasn't properly shutdown already:
		@EventHandler
		public void onPluginDisable(PluginDisableEvent event) {
			if (event.getPlugin().equals(plugin)) {
				disable();
			}
		}

		@EventHandler
		public void onServiceRegister(ServiceRegisterEvent event) {
			Class<?> service = event.getProvider().getService();
			this.updateService(service);
		}

		@EventHandler
		public void onServiceUnregister(ServiceUnregisterEvent event) {
			Class<?> service = event.getProvider().getService();
			this.updateService(service);
		}

		private void updateService(Class<?> service) {
			if (findVaultEconomy() && service == Economy.class) {
				// update:
				setupEconomy();
			}
		}
	}

	protected final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.0#");

	private PluginListener pluginListener = new PluginListener();
	private Plugin plugin;

	public EconomyController() {
	}

	/**
	 * Has to be called before using the controller.
	 * 
	 * <p>
	 * The controller has to be disabled again via {@link #disable()} when the plugin gets disabled. Otherwise the
	 * controller will disable itself automatically afterwards in order to perform proper cleanup.
	 * </p>
	 * 
	 * @param plugin
	 *            the plugin
	 */
	public void enable(Plugin plugin) {
		if (plugin == null) {
			throw new IllegalArgumentException("Plugin is null!");
		}
		if (!plugin.isEnabled()) {
			throw new IllegalArgumentException("Plugin is not enabled!");
		}
		this.plugin = plugin;

		// register listener:
		plugin.getServer().getPluginManager().registerEvents(pluginListener, plugin);

		// setup:
		this.setup();
	}

	/**
	 * Disables the controller.
	 * 
	 * <p>
	 * Has to be called when the plugin gets disabled. Otherwise the controller will disable itself automatically
	 * afterwards in order to perform proper cleanup. The controller should not be used while disabled.
	 * </p>
	 */
	public void disable() {
		// cleanup:
		this.cleanup();

		// unregister listener:
		HandlerList.unregisterAll(pluginListener);
	}

	/**
	 * Checks if the controller is enabled and can therefore be used.
	 * 
	 * @return <code>true</code> if enabled
	 */
	public boolean isEnabled() {
		return plugin != null;
	}

	protected final void validateIsEnabled() {
		if (!this.isEnabled()) {
			throw new IllegalStateException("Controller is currently disabled!");
		}
	}

	protected final Plugin getPlugin() {
		return plugin;
	}

	protected void setup() {
		// setup economy:
		this.setupEconomy();
	}

	protected void cleanup() {
		// cleanup economy:
		this.cleanupEconomy();
	}

	// ECONOMY:

	private Economy economy = null;

	protected boolean findVaultEconomy() {
		try {
			Class.forName("net.milkbowl.vault.economy.Economy");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	protected String getNoEconomyFoundMessage() {
		return "Could not find vault economy. Additional vault economy features disabled.";
	}

	protected String getNoEconomyServiceFoundMessage() {
		return "No vault economy service detected. Additional vault economy features disabled.";
	}

	protected String getEconomyServiceFoundMessage(Economy economy) {
		assert economy != null;
		return "Found vault economy service: " + economy.getName();
	}

	protected void setupEconomy() {
		// cleanup:
		this.cleanupEconomy();

		// check if vault economy is available:
		if (!this.findVaultEconomy()) {
			String message = this.getNoEconomyFoundMessage();
			if (message != null && !message.isEmpty()) {
				plugin.getLogger().info(message);
			}
			return;
		}

		// setup economy:
		RegisteredServiceProvider<Economy> economyService = Bukkit.getServicesManager().getRegistration(Economy.class);
		if (economyService != null) {
			economy = economyService.getProvider();
		}
		if (economy == null) {
			String message = this.getNoEconomyServiceFoundMessage();
			if (message != null && !message.isEmpty()) {
				plugin.getLogger().info(message);
			}
		} else {
			String message = this.getEconomyServiceFoundMessage(economy);
			if (message != null && !message.isEmpty()) {
				plugin.getLogger().info(message);
			}
		}
	}

	protected void cleanupEconomy() {
		economy = null;
	}

	public Economy getEconomy() {
		return economy;
	}

	/**
	 * Checks if an economy is available.
	 * 
	 * <p>
	 * This might have to be checked before attempting to use any economy related functions.
	 * </p>
	 * 
	 * @return <code>true</code> if an economy is available
	 */
	public boolean hasEconomy() {
		return economy != null && economy.isEnabled();
	}

	private void validateHasEconomy() {
		if (!this.hasEconomy()) {
			throw new IllegalArgumentException("No economy available!");
		}
	}

	/**
	 * Formats the given balance value.
	 * 
	 * @param value
	 *            the balance
	 * @return the formatted value
	 */
	public String formatBalance(double value) {
		return DECIMAL_FORMAT.format(value);
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
	public String applyChange(OfflinePlayer player, double deltaAmount, boolean withdrawPartial) {
		if (deltaAmount == 0.0D) return null;
		if (deltaAmount > 0.0D) {
			return this.depositMoney(player, deltaAmount);
		} else {
			deltaAmount = -deltaAmount;
			double balance = this.getBalance(player);
			if (balance < deltaAmount) {
				if (withdrawPartial) deltaAmount = balance;
				else return "Not enough money.";
			}
			return this.withdrawMoney(player, deltaAmount);
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
	public String depositMoney(OfflinePlayer player, double addAmount) {
		this.validateHasEconomy();
		if (player == null) {
			throw new IllegalArgumentException("Player is null!");
		}
		if (addAmount == 0.0D) return null;
		if (addAmount < 0.0D) return "Cannot deposit a negative amount.";

		EconomyResponse response = this.getEconomy().depositPlayer(player, addAmount);
		return this.getErrorMessage(response);
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
	public String withdrawMoney(OfflinePlayer player, double withdrawAmount) {
		this.validateHasEconomy();
		if (player == null) {
			throw new IllegalArgumentException("Player is null!");
		}
		if (withdrawAmount == 0.0D) return null;
		if (withdrawAmount <= 0.0D) return "Cannot withdraw a negative amount.";

		EconomyResponse response = this.getEconomy().withdrawPlayer(player, withdrawAmount);
		return this.getErrorMessage(response);
	}

	public String getErrorMessage(EconomyResponse response) {
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
	public double getBalance(OfflinePlayer player) {
		this.validateHasEconomy();
		if (player == null) {
			throw new IllegalArgumentException("Player is null!");
		}

		return this.getEconomy().getBalance(player);
	}
}
