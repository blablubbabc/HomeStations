package de.blablubbabc.homestations.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Utils {
	
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
		} catch(NumberFormatException e) {
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
