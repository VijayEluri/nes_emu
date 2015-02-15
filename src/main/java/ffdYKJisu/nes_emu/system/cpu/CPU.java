package ffdYKJisu.nes_emu.system.cpu;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Shorts;

import ffdYKJisu.nes_emu.domain.AddressingMode;
import ffdYKJisu.nes_emu.domain.Opcode;
import ffdYKJisu.nes_emu.domain.StatusBit;
import ffdYKJisu.nes_emu.exceptions.AddressingModeException;
import ffdYKJisu.nes_emu.system.HexUtils;
import ffdYKJisu.nes_emu.system.memory.CPUMemory;

/**
 * Controls all functions of the main CPU of the NES.
 * Handles all opcode processing and registers of the cpu.
 * The cart is loaded and restarted and emulation begins in this class.
 * @author fe01106
 */
public class CPU implements ICPU {

	private static Logger logger = LoggerFactory.getLogger(CPU.class);

	/**
	 * Program counter, holds memory location of current position
	 */
	private short PC;
	/** Accumulator */
	private byte A;
	/** Index register X */
	private byte X;
	/** Index register X */
	private byte Y;
	/** Holds the bits of the status byte for the processor */
	private StatusBit P;
	private final CPUMemory memory;
	//private int cyclesRun;
	private byte _stackPointer;

	private static short RESET_VECTOR_LOW = (short) 0xFFFC;
	private static short RESET_VECTOR_HIGH = (short) 0xFFFD;
	
	private static short INTERRUPT_VECTOR_LOW = (short) 0xFFFE;
	private static short INTERRUPT_VECTOR_HIGH = (short) 0xFFFF;
	
	public CPU(CPUMemory memory_) {		
		logger.info("CPU has been initiated");	
		memory = memory_;
		//cyclesRun = 0;
		// Set up State registers
		initStateRegisters();
		// Load cart into memory
		// Loads cartridge banks to cpu memory banks
		//memory.writeCartToMemory(cart);
	}

	private void initStateRegisters() {
		P = new StatusBit();
		// Processor status
		P.clearCarry();
		P.clearZero();
		P.clearDecimal();
		P.clearInterruptDisable();
		P.clearDecimal();
		P.clearOverflow();
		P.clearNegative();

		// A,X,Y
		A = 0;
		X = 0;
		Y = 0;

		// Stack pointer
		_stackPointer = (byte) 0xFF;
	}

	private void incrementPC(int increment) {
		for ( int i=0; i < increment; i++ ) {
			incrementPC();
		}
	}
	
	private void incrementPC() {
		PC++;
	}
	
	/**
	 * Runs the CPU for one operation regardless of how long it will take
	 * @return returns how many cycles the step took
	 */
	public void runStep() {
		// Read Opcode from PC
		Opcode op = getOpcode();
		
		byte opcodeBytes = op.getOpcodeBytes();
		
		// Print instruction to logger
		logger.info("Got instruction {} opcode {} with bytes {} at PC {}", new Object[]{instructionToString(PC), op, HexUtils.toHex(opcodeBytes), HexUtils.toHex(PC)});
		
		Byte result;
		
		if(op.readsMemory()) {
			byte operand = getOperand(op.getAddressingMode(), getAddress(op.getAddressingMode()));
			result = doOperation(op, operand);
		} else {
			result = doOperation(op);
		}

		persistResult(op, result);
		
		// Print CPU state to log
		// Process instructions for op
		//int cyclesBefore = this.cyclesRun;
		// this.processOp(op, operand);
		// Increment PC
		incrementPC(op.getLength());
		
		// Return time taken
	}

	private void persistResult(Opcode op_, Byte result_) {
		// void functions return null. Don't have to do anything.
		if(result_ == null) { return; }
		
		switch(op_.getAddressingMode()) {
			case ABSOLUTE:
			case ABSOLUTE_X:
			case ABSOLUTE_Y:
			case INDIRECT:
			case INDIRECT_X:
			case INDIRECT_Y:
			case ZERO_PAGE:
			case ZERO_PAGE_X:
			case ZERO_PAGE_Y:			
				short address = getAddress(op_.getAddressingMode());
				logger.info("Persisting result {} from operation {} to {}", new Object[] {HexUtils.toHex(result_), op_, HexUtils.toHex(address)});
				memory.write(address, result_);
				break;
			case ACCUMULATOR:
				logger.info("Persisting result {} from operation {} to A", HexUtils.toHex(result_), op_);
				A = result_;		
				break;
			case IMMEDIATE: // Some ops read immediate results but these should return results
			case IMPLICIT:
			case RELATIVE: // all branching functions don't have results			
			default:
				throw new UnsupportedOperationException();
		}		
	}

	// TODO: generalize doOperation with one or zero operands
	private Byte doOperation(Opcode op_) {		
		try {
			Method opCodeImplementation = getClass().getDeclaredMethod(op_.getCodeName());
			return (Byte) opCodeImplementation.invoke(this);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return null;
	}
	
	private Byte doOperation(Opcode op_, byte operand_) {		
		try {	
			logger.info("Looking for method with name {} for operation {} with operands {}", 
					new Object[] {op_.getCodeName(), op_, operand_});
			Method opCodeImplementation = getClass().getDeclaredMethod(op_.getCodeName(), byte.class);
			logger.info("Found method {} for op {}, calling with {} operands of length {}", 
					new Object[] {opCodeImplementation, op_, operand_, 1});							
			return (Byte) opCodeImplementation.invoke(this, operand_);
		} catch (NoSuchMethodException e) {
			logger.error("{}", e);
			throw new UnsupportedOperationException();
		} catch (SecurityException e) {
			throw new UnsupportedOperationException();
		} catch (IllegalAccessException e) {
			throw new UnsupportedOperationException();
		} catch (IllegalArgumentException e) {
			logger.error("{}", e);
			throw new UnsupportedOperationException();
		} catch (InvocationTargetException e) {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * Reads the instruction at that address. Creates a string that will readable
	 * and will look like "$FF00: ($D3 $F0)    ADD $F0".
	 * @param address Location where you want to read an instruction from
	 * @return A string formatted for debugger display.
	 */
	public String instructionToString(short address) {
		StringBuffer sb = new StringBuffer(HexUtils.toHex(address) + ": ");
		int instructionLength = this.instructionLength(address);

		byte[] bytes = new byte[instructionLength];

		StringBuffer sbBytes = new StringBuffer();		
		
		sbBytes.append("(");
		for (int j = 0; j < instructionLength; j++) {
			byte b = memory.read(address);
			sbBytes.append(HexUtils.toHex(b));
			bytes[j] = b;
			if (j != instructionLength - 1)
				sbBytes.append(" ");
			address++;
		}
		sbBytes.append(")");
		// Pad string to maximum length of bytes possible for one instruction
		// That is 3 bytes which is ten characters: "(.. .. ..)"
		int maxLength = 13;
		while (sbBytes.length() < maxLength) {
			sbBytes.append(" ");
		}
		sb.append(sbBytes);
		String opcodeName = Opcode.getOpcodeByBytes(bytes[0]).getCodeName();
		sb.append(" " + opcodeName + " ");

		for (int j = 1; j < instructionLength; j++) {
			sb.append(bytes[j]);
			if (j != instructionLength - 1)
				sb.append(" ");
		}
		return sb.toString();
	}

	/**
	 * Returns the number of bytes that the current instruction occupies. This
	 * includes the actual instruction opcode itself. I.e. CLD returns 1 even 
	 * though it has no parameters
	 * @param address Address at which the instruction is at
	 * @return Number of bytes until next instruction
	 */
	public int instructionLength(short address) {
		return Opcode.getOpcodeByBytes(memory.read(address)).getLength();
	}

	private byte getOperand(AddressingMode mode_, short address_) {
		switch (mode_) {
		case IMPLICIT:
			throw new UnsupportedOperationException();
		case ACCUMULATOR:
			return A;
		case IMMEDIATE:
		case ZERO_PAGE:			
		case ZERO_PAGE_X:
		case ZERO_PAGE_Y:
		case RELATIVE:
		case ABSOLUTE:
		case ABSOLUTE_X:
		case ABSOLUTE_Y:
		case INDIRECT:
		case INDIRECT_X:
		case INDIRECT_Y:
			return memory.read(address_);
		default:
			logger.error("No matching addressing mode for {}", mode_);
			throw new UnsupportedOperationException();
		}
	}
	
	/**
	 * Reads current PC, reads the opcode there, determines the addressing mode
	 * and returns an address by determining what bytes to read from the parameters
	 * of the instruction. The address returned is either the read/write address
	 * for the instruction
	 * @return Address of where to perform operation
	 */	
	private short getAddress(AddressingMode mode_) {
		short addr = 0;
				
		switch (mode_) {
			case IMPLICIT:
				break;
			case ACCUMULATOR:
				break;
			case RELATIVE:
			case IMMEDIATE:
				addr = (short) (PC + 1);
				break;
			case ZERO_PAGE:
				addr = memory.read((short)(PC + 1));
				break;
			case ZERO_PAGE_X:
				byte zpAddrX = memory.read((short)(PC + 1));
				addr = (short)(zpAddrX + X);
				break;
			case ZERO_PAGE_Y:
				byte zpAddrY = memory.read((short)(PC + 1));
				addr = (short)(zpAddrY + Y);
				break;
			case ABSOLUTE:
				addr = (short) (readShort((short) (PC + 1)));
				break;
			case ABSOLUTE_X:
				addr = (short) (readShortIndirect((short) (PC + 1), X));
				break;
			case ABSOLUTE_Y:
				addr = (short) (readShortIndirect((short) (PC + 1), Y));
				break;
			case INDIRECT:
				addr = readShort((short) (PC + 1));
				addr = readShort(addr);
				break;
			case INDIRECT_X:
				addr = (short) (readShortIndirect((short) (PC + 1), X));
				addr = readShort(addr);
				break;
			case INDIRECT_Y:
				addr = (short) (readShort((short) (PC + 1)));
				addr = readShortIndirect(addr, Y);
				break;
			default:
				logger.error("No matching addressing mode for {}", mode_);
				throw new AddressingModeException(mode_.toString());
		}
		
		logger.info("At PC {} with mode {}. Got final address {}", new Object[]{HexUtils.toHex(PC), mode_, HexUtils.toHex(addr)});
		return addr;
	}
	
	/** Reads an address for two consecutive bytes and forms
	 * that into an address */
	private short readShort(short address) {
		return Shorts.fromBytes(
			memory.read((short)(address + 1)),
			memory.read((address)
		));
	}
	
	private short readShortIndirect(short address, byte offset) {
		return (short) (readShort(address) + offset);
	}

	/**
	 * Retrieves the next opcode from memory and returns it 
	 * @return opCode
	 */
	private Opcode getOpcode() {
		byte b = memory.read(PC);		
		Opcode o = Opcode.getOpcodeByBytes(b);
		logger.info("Reading opcode at PC addr {}. Got byte {} and opcode {}", new Object[] {HexUtils.toHex(PC), HexUtils.toHex(b), o});
		return o;
	}
	
	/* ******************* 
	 * Logic
	 ******************* */
	
	public void AND(byte val_) {
		A = (byte) (A & val_);
		setZero(A);
		setNegative(A);		
	}
	
	public byte ASL(byte val_) {
		P.setCarry((val_ & 0x80) != 0);
		return shift(val_, 1, false);		
	}
	
	public void BIT(byte val_) {
		setZero((byte) (A & val_));
		setNegative(val_); // set if value is negative
		P.setOverflow((val_& 1 << 6) != 0); // Set overflow to value of bit 6
	}
	
	public void EOR(byte val_) {
		A ^= val_;
		setZero(A);
		setNegative(A);
	}
	
	public void ORA(byte val_) {
		A |= val_;
		setZero(A);
		setNegative(A);
	}
	
	public byte LSR(byte val_) {
		P.setCarry((val_ & 0x01) != 0);
		return shift(val_, -1, false);
	}
	
	public byte ROL(byte val_) {
		boolean carry = (val_ & 0x80) != 0;
		P.setCarry(carry);
		return shift(val_, 1, carry);
	}
	
	public byte ROR(byte val_) {
		boolean carry = (val_ & 0x01) != 0;
		P.setCarry(carry);
		return shift(val_, -1, carry);
	}
	
	/** positive shiftAmount <<, 
	 *  negative shiftAmount >>
	 */ 	
	private byte shift(byte val_, int direction_, boolean carry_) {
		if(direction_ > 0) {
			val_ <<= 1; // do shift
			val_ = (byte) (carry_ ? val_ | 1 : val_ & ~1); // account for carry
		} else {
			val_ >>= 1; 
			val_ = (byte) (carry_ ? val_ | (1 << 8): val_ & ~(1 << 8)); 
		}
		setZero(val_);
		setNegative(val_);
		return val_;
	}
	
	/* ******************* 
	 * Arithmetic
	 ******************* */
	
	public void ADC(byte val_) { add(true, val_); }
	
	public void SBC(byte val_) { add(false, val_); }
	
	public void add(boolean isAdding_, byte val_) {
		int temp = Byte.toUnsignedInt(A);
		if(isAdding_) {
			temp = temp + Byte.toUnsignedInt(val_) + (P.isSetCarry() ? 1 : 0);			
		} else {
			temp = temp - Byte.toUnsignedInt(val_) - (P.isSetCarry() ? 1 : 0);			
		}
		P.setCarry(temp > 0xFF);
		byte initialA = A;
		A = (byte) temp;
		setZero(A);
		setNegative(A);
		setOverflow(initialA, A);
		logger.info("{} {} and {}, got {} with status {}", new Object[] {
				isAdding_ ? "Added" : "Subtracted", 
				HexUtils.toHex(val_),
				HexUtils.toHex(initialA),
				HexUtils.toHex(A),
				P
		});
	}

	public void INX() { X = increment(X, 1); }
	
	public void DEX() { X = increment(X, -1); }
	
	public void INY() { Y = increment(Y, 1); }
	
	public void DEY() { Y = increment(Y, -1); }
		
	public byte INC(byte val_) { return increment(val_, 1); }
	
	public byte DEC(byte val_) { return increment(val_, -1); } 
	
	private byte increment(byte val_, int increment_) {
		byte result = (byte) (val_ + increment_);
		setNegative(result);
		setZero(result);
		return result;
	}

	private void setOverflow(byte initial_, byte final_) {
		P.setOverflow((final_ & (byte)0x80) != (initial_ & 0x80));
	}
	
	private void setZero(byte val_) {
		P.setZero((val_ & 0xFF) == 0);
	}
	
	private void setNegative(byte val_) {
		P.setNegative(val_ < 0);	
	}	
	
	/* ******************* 
	 * Branches
	 ******************* */
	
	public void BCS(byte val_) { branch(P.isSetCarry(), val_); }
	
	public void BCC(byte val_) { branch(!P.isSetCarry(), val_); }	
		
	public void BEQ(byte val_) { branch(P.isSetZero(), val_); }
	
	public void BNE(byte val_) { branch(!P.isSetZero(), val_); }
	
	public void BMI(byte val_) { branch(P.isSetNegative(), val_); }
	
	public void BPL(byte val_) { branch(!P.isSetNegative(), val_); }
	
	public void BVS(byte val_) { branch(P.isSetOverflow(), val_); }
	
	public void BVC(byte val_) { branch(!P.isSetOverflow(), val_); }
	
	private void branch(boolean status_, byte offset_) {
		if(status_) {
			PC += offset_;
		}
	}
	
	public void JMP(short address_) {
		PC = address_;
	}
	
	public void JSR(short address_) {
		PC--;
		pushPC();
		PC = address_;
	}
	
	public void RTS() {
		byte lowAddr = pop();
		PC = (short) (Shorts.fromBytes(pop(), lowAddr) + 1);
	}
	
	public void BRK() {		
		pushPC();
		P.setBreak();
		push(P.asByte());
		P.setInterruptDisable();
		setPCFromVector(INTERRUPT_VECTOR_LOW, INTERRUPT_VECTOR_HIGH);		
	}
	
	public void RTI() {
		P.fromByte(pop());
		byte lowAddr = pop();
		PC = Shorts.fromBytes(pop(), lowAddr);
	}
	
	/* ******************* 
	 * Loads
	 ******************* */
	
	public void LDA(byte val_) {
		A = val_;
		setNegative(A);
		setZero(A);
	}
		
	public void LDX(byte val_) {
		X = val_;
		setNegative(X);
		setZero(X);
	}
	
	public void LDY(byte val_) {
		Y = val_;
		setNegative(Y);
		setZero(Y);
	}

	/* ******************* 
	 * Clears 
	 ******************* */
	
	public void CLC() { P.clearCarry(); }

	public void CLD() { P.clearDecimal(); }
	
	public void CLI() { P.clearInterruptDisable(); }

	public void CLV() { P.clearOverflow(); }
	
	/* ******************* 
	 * Compares 
	 ******************* */
	
	public void CMP(byte val_) { compare(A, val_); }
	
	public void CPX(byte val_) { compare(X, val_); }
	
	public void CPY(byte val_) { compare(Y, val_); }
	
	/**
	 * Compares two bytes, used by all comparison operations.
	 * Simulates A - B and changes status flags based on results
	 * @param A minuend (usually a register)
	 * @param B subtrahend (usually from memory)
	 */		
	private void compare(byte a_, byte b_) {
		int result = Byte.toUnsignedInt(a_) - Byte.toUnsignedInt(b_);
		P.setCarry(result >= 0);
		P.setZero(result == 0);
		P.setNegative(result < 0);
	}
	
	/* ******************* 
	 * Stack
	 ******************* */
	
	public void PHA() {
		push(A);
	}
	
	public void PHP() {
		push(P.asByte());
	}
	
	public void PLA() {
		A = pop();
	}
	
	public void PLP() {		
		P.fromByte(pop());
	}
		
	private void push(byte val_) {
		memory.push(_stackPointer, val_);
		_stackPointer--;	
	}
	
	private byte pop() {
		_stackPointer++;
		return memory.pop(_stackPointer);		
	}
	
	private void pushPC() {
		byte[] bytesPC = Shorts.toByteArray(PC);
		push(bytesPC[0]); // Upper
		push(bytesPC[1]); // Lower
	}
	
	
	/* ******************* 
	 * Other
	 ******************* */

	public void NOP() {}
		
	/* ******************* 
	 * Sets
	 ******************* */		

	public void SEC() { P.setCarry(); }
	
	public void SED() { P.setDecimal(); }
	
	public void SEI() { P.setInterruptDisable(); }	
	
	/* ******************* 
	 * Stores
	 ******************* */
	
	public byte STA() { return A; }
	
	public byte STX() { return X; }
	
	public byte STY() { return Y; }
	
	/* ******************* 
	 * Transfers
	 ******************* */

	public void TAX() {
		X = A;
		setNegative(X);
		setZero(X);
	}

	public void TAY() {
		Y = A;
		setNegative(Y);
		setZero(Y);
	}

	public void TSX() {
		X = _stackPointer;
		setNegative(X);
		setZero(X);
	}
	
	public void TXS() {
		_stackPointer = X;
	}

	public void TYA() {
		A = Y;
		setNegative(A);
		setZero(A);
	}
	
	/* ******************* 
	 * Helper functions
	 ******************* */
	
	/**
	 * Accepts two addresses and returns true if those two addresses are
	 * on different pages. I.e. return false if their upper bytes are not 
	 * equal.
	 * @param startAddress Starting address, typically the PC of the instruction
	 * @param endAddress End address, typically the location that is to be
	 * written to. 
	 * @return Returns true if the two addresses lie on different pages,
	 * false otherwise.
	 */
	boolean pageJumped(short startAddress, short endAddress) {
		// return !startAddress.getUpper().equals(endAddress.getUpper());
		return false;
	}

	public void reset() {
		setPCFromVector(RESET_VECTOR_LOW, RESET_VECTOR_HIGH);
	}
	
	private void setPCFromVector(short vectorLow_, short vectorHigh_) {
		short address = Shorts.fromBytes(memory.read(vectorHigh_), memory.read(vectorLow_));
		logger.info( "Jumping to {} from vector {} {}", new Object[] {
			HexUtils.toHex(address),
			HexUtils.toHex(vectorHigh_),
			HexUtils.toHex(vectorLow_),
		});		
		PC = address;
	}
	
	public short getPC() { return PC; }		
	public byte getSP() { return _stackPointer; }
	public byte getSR() { return P.asByte(); }
	public byte getA() { return A; }
	public byte getX() { return X; }
	public byte getY() { return Y; }
	public boolean getCarryFlag() { return P.isSetCarry(); }
	public boolean getZeroFlag() { return P.isSetZero(); }
	public boolean getInterruptDisable() { return P.isSetInterruptDisable(); }
	public boolean getDecimalMode() { return P.isSetDecimal(); }
	public boolean getBreakCommand() { return P.isSetBreak(); }
	public boolean getOverflowFlag() { return P.isSetOverflow(); }
	public boolean getNegativeFlag() { return P.isSetNegative(); }
}
