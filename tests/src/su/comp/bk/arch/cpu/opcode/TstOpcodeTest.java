/*
 * Created: 09.04.2012
 *
 * Copyright (C) 2012 Victor Antonovich (v.antonovich@gmail.com)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package su.comp.bk.arch.cpu.opcode;

import static org.junit.Assert.*;

import org.junit.Test;

import su.comp.bk.arch.Computer;
import su.comp.bk.arch.cpu.Cpu;
import su.comp.bk.arch.io.Sel1RegisterSystemBits;
import su.comp.bk.arch.memory.ReadOnlyMemory;

/**
 * TST operation tests.
 */
public class TstOpcodeTest {

    private static final int PSW_STATE = 0340;

    @Test
    public void testTstInstructionExecute() {
        Computer computer = new Computer();
        computer.addDevice(new Sel1RegisterSystemBits(0100000));
        computer.addMemory(new ReadOnlyMemory(0100000, new short[] {
                TstOpcode.OPCODE, // TST R0
                (short) (TstOpcode.OPCODE | Opcode.BYTE_OPERATION_FLAG), // TSTB R0
                }));
        computer.reset();
        // TST R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());
        // TSTB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_Z, computer.getCpu().getPswState());

        computer.getCpu().writeRegister(false, Cpu.PC, 0100000);
        // TST R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0100000);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100002, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());
        // TSTB R0
        computer.getCpu().writeRegister(false, Cpu.R0, 0200);
        computer.getCpu().executeSingleInstruction();
        assertEquals(0100004, computer.getCpu().readRegister(false, Cpu.PC));
        assertEquals(PSW_STATE | Cpu.PSW_FLAG_N, computer.getCpu().getPswState());

    }

}
