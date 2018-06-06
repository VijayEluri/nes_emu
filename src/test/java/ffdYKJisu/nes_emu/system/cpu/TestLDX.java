package ffdYKJisu.nes_emu.system.cpu;

import ffdYKJisu.nes_emu.exceptions.UnableToLoadRomException;
import ffdYKJisu.nes_emu.system.NES;
import ffdYKJisu.nes_emu.system.cartridge.Cartridge;
import ffdYKJisu.nes_emu.system.cartridge.CartridgeFactory;
import ffdYKJisu.nes_emu.system.memory.Addressable;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestLDX {

	NES _n;
	CPU _c;
	Addressable _mem;
	
	@Before
	public void initialize() throws UnableToLoadRomException {
		Cartridge c =
						new CartridgeFactory()
										.fromInputStream(ClassLoader.getSystemResourceAsStream("Pac-Man (U) [!].nes"));
		NES _nes = new NES(c);
		_c = _nes.getCPU();
		_mem = _c.getMemory();
		_c.reset();
	}
	
	@Test
	public void testSetZero() {
		_c.LDX((byte) 0);
		assertEquals((byte) 0, _c.getX());
		assertTrue(_c.getZeroFlag());	
	}

	@Test
	public void testResetZero() {
		_c.LDX((byte) 1);
		assertEquals((byte) 1, _c.getX());
		assertTrue(!_c.getZeroFlag());
	}

	@Test
	public void testSetNegative() {
		_c.LDX((byte) 0xFF);
		assertEquals((byte) 0xFF, _c.getX());
		assertTrue(_c.getNegativeFlag());
	}
	
	@Test
	public void testResetNegative() {
		_c.LDX((byte) 10);
		assertEquals((byte) 10, _c.getX());
		assertTrue(!_c.getNegativeFlag());
	}
	
}
