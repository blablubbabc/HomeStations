/**
 * Copyright (c) blablubbabc <http://www.blablubbabc.de>
 * All rights reserved.
 */
package de.blablubbabc.homestations.utils;

import java.util.logging.Logger;

import org.bukkit.plugin.Plugin;

public class Log {

	private static Logger logger;

	public static void init(Plugin pl) {
		logger = pl.getLogger();
	}

	public static void log(String message) {
		System.out.println(message);
	}

	public static void info(String message) {
		logger.info(message);
	}

	public static void warning(String message) {
		logger.warning(message);
	}

	public static void severe(String message) {
		logger.severe(message);
	}
}
