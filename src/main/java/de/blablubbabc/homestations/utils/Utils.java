/**
 * Copyright (c) blablubbabc <http://www.blablubbabc.de>
 * All rights reserved.
 */
package de.blablubbabc.homestations.utils;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

public class Utils {

	public static String replacePlaceholders(String message, String... placeholders) {
		if (message != null && placeholders != null) {
			for (int i = 1; i < placeholders.length; i += 2) {
				message = replacePlaceholder(message, placeholders[i - 1], placeholders[i]);
			}
		}
		return message;
	}

	public static String replacePlaceholder(String message, String placeholder, String value) {
		if (message == null || placeholder == null || value == null) return message;
		return message.replace("{" + placeholder + "}", value);
	}

	public static void sendMessage(Player player, String message) {
		if (player == null) return;
		if (message == null || message.isEmpty()) return;

		player.sendMessage(message);
	}

	public static void playFireworkEffect(Location loc, FireworkEffect effect) {
		// spawn firework entity:
		Firework firework = loc.getWorld().spawn(loc, Firework.class);
		// apply given firework effect:
		FireworkMeta fireworkMeta = firework.getFireworkMeta();
		fireworkMeta.addEffect(effect);
		firework.setFireworkMeta(fireworkMeta);
		// play firework explosion effect:
		firework.playEffect(EntityEffect.FIREWORK_EXPLODE);
		// remove firework entity again (before it ticks and plays launching effects):
		firework.remove();
	}

	// LOCATIONS TO / FROM STRING

	public static String LocationToString(Location loc) {
		return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
	}

	public static Location StringToLocation(String string) {
		if (string == null) return null;
		String[] split = string.split(";");
		if (split.length != 4 && split.length != 6) return null;

		World world = Bukkit.getWorld(split[0]);
		if (world == null) return null;
		Double x = parseDouble(split[1]);
		if (x == null) return null;
		Double y = parseDouble(split[2]);
		if (y == null) return null;
		Double z = parseDouble(split[3]);
		if (z == null) return null;

		Float yaw = 0.0F;
		Float pitch = 0.0F;
		if (split.length == 6) {
			yaw = parseFloat(split[4]);
			if (yaw == null) yaw = 0.0F;
			pitch = parseFloat(split[5]);
			if (pitch == null) pitch = 0.0F;
		}

		return new Location(world, x, y, z, yaw, pitch);
	}

	public static Integer parseInteger(String string) {
		try {
			return Integer.parseInt(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static Float parseFloat(String string) {
		try {
			return Float.parseFloat(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static Double parseDouble(String string) {
		try {
			return Double.parseDouble(string);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
