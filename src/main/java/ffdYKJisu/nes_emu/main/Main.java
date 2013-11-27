/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ffdYKJisu.nes_emu.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ffdYKJisu.nes_emu.debugger.Debugger;
import ffdYKJisu.nes_emu.exceptions.UnableToLoadRomException;
import ffdYKJisu.nes_emu.system.Cartridge;
import ffdYKJisu.nes_emu.system.NES;

/**
 * 
 * @author fe01106
 */
public class Main {

	static final Logger logger = LoggerFactory.getLogger(Main.class);

	/**
	 * 
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) {

		String romName = "Pac-Man (U) [!].nes";

		try {
			Cartridge pacmanCart = getCartridge(romName);
			NES nes = new NES();
			nes.setCart(pacmanCart);
			Debugger d = new Debugger(nes);
			d.startConsole();
		} catch (UnableToLoadRomException e) {
			logger.error("Failed to load rom {}", romName);
		}

	}

	public static Cartridge getCartridge(String resourcePath)
			throws UnableToLoadRomException {
		InputStream pacmanIs = Main.class.getClassLoader().getResourceAsStream(
				resourcePath);

		if (pacmanIs == null) {
			logger.error("Failed to load cartridge");
			throw new UnableToLoadRomException();
		}

		Cartridge cart = new Cartridge(pacmanIs);

		return cart;
	}
}
