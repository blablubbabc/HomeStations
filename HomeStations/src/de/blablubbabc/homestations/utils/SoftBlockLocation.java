package de.blablubbabc.homestations.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class SoftBlockLocation {

	private String worldName;
	private int x;
	private int y;
	private int z;

	public SoftBlockLocation(Location location) {
		this(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	public SoftBlockLocation(String worldName, int x, int y, int z) {
		this.worldName = worldName;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public String getWorldName() {
		return worldName;
	}

	public void setWorldName(String worldName) {
		this.worldName = worldName;
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = x;
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = y;
	}

	public int getZ() {
		return z;
	}

	public void setZ(int z) {
		this.z = z;
	}

	public Location getBukkitLocation() {
		World world = Bukkit.getServer().getWorld(worldName);
		if (world == null) return null;
		return new Location(world, x, y, z);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final SoftBlockLocation other = (SoftBlockLocation) obj;

		if (this.worldName != other.worldName && (this.worldName == null || !this.worldName.equals(other.worldName))) {
			return false;
		}

		if (this.x != other.x) {
			return false;
		}

		if (this.y != other.y) {
			return false;
		}

		if (this.z != other.z) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int hash = 3;

		hash = 19 * hash + (this.worldName != null ? this.worldName.hashCode() : 0);
		hash = 19 * hash + this.x ^ (this.x >>> 32);
		hash = 19 * hash + this.y ^ (this.y >>> 32);
		hash = 19 * hash + this.z ^ (this.z >>> 32);

		return hash;
	}

	@Override
	public String toString() {
		return worldName + ";" + x + ";" + y + ";" + z;
	}

	// statics

	public static List<SoftBlockLocation> getFromStringList(Collection<String> strings) {
		List<SoftBlockLocation> softLocs = new ArrayList<SoftBlockLocation>();
		for (String s : strings) {
			SoftBlockLocation soft = getFromString(s);
			if (soft != null) softLocs.add(soft);
		}
		return softLocs;
	}

	public static List<String> toStringList(Collection<SoftBlockLocation> softLocs) {
		List<String> strings = new ArrayList<String>();
		for (SoftBlockLocation soft : softLocs) {
			if (soft != null) strings.add(soft.toString());
		}
		return strings;
	}

	public static SoftBlockLocation getFromString(String string) {
		if (string == null) return null;
		String[] split = string.split(";");
		if (split.length != 4) return null;

		String worldName = split[0];
		if (worldName == null) return null;
		Integer x = Utils.parseInteger(split[1]);
		if (x == null) return null;
		Integer y = Utils.parseInteger(split[2]);
		if (y == null) return null;
		Integer z = Utils.parseInteger(split[3]);
		if (z == null) return null;

		return new SoftBlockLocation(worldName, x, y, z);
	}
}
