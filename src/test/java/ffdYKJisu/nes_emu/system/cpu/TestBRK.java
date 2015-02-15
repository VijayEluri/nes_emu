package ffdYKJisu.nes_emu.system.cpu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Shorts;

import ffdYKJisu.nes_emu.exceptions.UnableToLoadRomException;
import ffdYKJisu.nes_emu.system.Cartridge;
import ffdYKJisu.nes_emu.system.NES;
import ffdYKJisu.nes_emu.system.memory.CPUMemory;

public class TestBRK {

	private static final Logger logger = LoggerFactory.getLogger(TestBRK.class);
	
	NES _n;
	CPU _c;
	CPUMemory _mem; 
	
	@Before
	public void initialize() throws UnableToLoadRomException {
		Cartridge c = new Cartridge(ClassLoader.getSystemResourceAsStream("Pac-Man (U) [!].nes"));
		_mem = new CPUMemory();
		_c = new CPU(_mem);
		_mem.writeCartToMemory(c);
		_c.reset();
	}
	
	@Test
	public void testBRK() {
		// before
		byte[] bytesPC = Shorts.toByteArray(_c.getPC());
		logger.info("Got PC array of {}", Arrays.toString(bytesPC));
		
		// op
		_c.BRK();
		
		// after
		short interruptVector = Shorts.fromBytes(_mem.read((short) 0xFFFF), _mem.read((short) 0xFFFE));
		assertEquals(interruptVector, _c.getPC());
		assertTrue(_c.getInterruptDisable());
		assertEquals((byte) (0xFF - 3),_c.getSP());
		assertEquals(bytesPC[0], _mem.read((short) (0x100 + 0xFF)));
		assertEquals(bytesPC[1], _mem.read((short) (0x100 + 0xFE)));
	}
	
}
