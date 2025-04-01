/*
 * Copyright (c) BELLSOFT. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

// Benchmark compares switch-based control flow with try/catch exception handling (using multiple catch blocks).
// Intel
//   MultipleCatchBlocks.multipleCatch  avgt    5      6.224     0.018  ns/op
//   MultipleCatchBlocks.switchCase     avgt    5      3.156     0.019  ns/op
// AARCH
//   MultipleCatchBlocks.multipleCatch  avgt    5      6.452     0.016  ns/op
//   MultipleCatchBlocks.switchCase     avgt    5      3.415     0.002  ns/op

package org.openjdk.bench.vm.runtime.exceptions;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
public class MultipleCatchBlocks {

    // Base class for our exceptions that carries a char value.
    static abstract class IntResult extends Exception {
        public final char data;
        public IntResult(char data) { this.data = data; }
    }

    // 26 uppercase exception types.
    static class Result_A extends IntResult { public Result_A() { super('A'); } }
    static class Result_B extends IntResult { public Result_B() { super('B'); } }
    static class Result_C extends IntResult { public Result_C() { super('C'); } }
    static class Result_D extends IntResult { public Result_D() { super('D'); } }
    static class Result_E extends IntResult { public Result_E() { super('E'); } }
    static class Result_F extends IntResult { public Result_F() { super('F'); } }
    static class Result_G extends IntResult { public Result_G() { super('G'); } }
    static class Result_H extends IntResult { public Result_H() { super('H'); } }
    static class Result_I extends IntResult { public Result_I() { super('I'); } }
    static class Result_J extends IntResult { public Result_J() { super('J'); } }
    static class Result_K extends IntResult { public Result_K() { super('K'); } }
    static class Result_L extends IntResult { public Result_L() { super('L'); } }
    static class Result_M extends IntResult { public Result_M() { super('M'); } }
    static class Result_N extends IntResult { public Result_N() { super('N'); } }
    static class Result_O extends IntResult { public Result_O() { super('O'); } }
    static class Result_P extends IntResult { public Result_P() { super('P'); } }
    static class Result_Q extends IntResult { public Result_Q() { super('Q'); } }
    static class Result_R extends IntResult { public Result_R() { super('R'); } }
    static class Result_S extends IntResult { public Result_S() { super('S'); } }
    static class Result_T extends IntResult { public Result_T() { super('T'); } }
    static class Result_U extends IntResult { public Result_U() { super('U'); } }
    static class Result_V extends IntResult { public Result_V() { super('V'); } }
    static class Result_W extends IntResult { public Result_W() { super('W'); } }
    static class Result_X extends IntResult { public Result_X() { super('X'); } }
    static class Result_Y extends IntResult { public Result_Y() { super('Y'); } }
    static class Result_Z extends IntResult { public Result_Z() { super('Z'); } }

    // 6 additional exceptions (lowercase) to complete 32 types.
    static class Result_a extends IntResult { public Result_a() { super('a'); } }
    static class Result_b extends IntResult { public Result_b() { super('b'); } }
    static class Result_c extends IntResult { public Result_c() { super('c'); } }
    static class Result_d extends IntResult { public Result_d() { super('d'); } }
    static class Result_e extends IntResult { public Result_e() { super('e'); } }
    static class Result_f extends IntResult { public Result_f() { super('f'); } }

    // Create an array of 32 exception instances.
    static final IntResult[] data1 = new IntResult[] {
        new Result_A(), new Result_B(), new Result_C(), new Result_D(),
        new Result_E(), new Result_F(), new Result_G(), new Result_H(),
        new Result_I(), new Result_J(), new Result_K(), new Result_L(),
        new Result_M(), new Result_N(), new Result_O(), new Result_P(),
        new Result_Q(), new Result_R(), new Result_S(), new Result_T(),
        new Result_U(), new Result_V(), new Result_W(), new Result_X(),
        new Result_Y(), new Result_Z(), new Result_a(), new Result_b(),
        new Result_c(), new Result_d(), new Result_e(), new Result_f()
    };

    // For the switch-based benchmark we use only 26 uppercase letters.
    static final char[] data2 = new char[] {
        'A','B','C','D','E','F','G','H','I','J','K','L','M',
        'N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
        'a','b','c','d','e','f'
    };

    // A global counter used to index into array
    static long counter = 0;

    static final int[] arr = new int[128];  // large enough for ASCII codes

    // getValue1 throws a pre-allocated exception
    static char getValue1() throws IntResult {
        throw data1[(int)(counter++ % data1.length)];
    }

    // getValue2 returns a char from data2[]
    static char getValue2() {
        return data2[(int)(counter++ % data2.length)];
    }

    @Benchmark
    public int multipleCatch() {
        try {
            getValue1();
        }
        catch (Result_A e) { return arr['A']; }
        catch (Result_B e) { return arr['B']; }
        catch (Result_C e) { return arr['C']; }
        catch (Result_D e) { return arr['D']; }
        catch (Result_E e) { return arr['E']; }
        catch (Result_F e) { return arr['F']; }
        catch (Result_G e) { return arr['G']; }
        catch (Result_H e) { return arr['H']; }
        catch (Result_I e) { return arr['I']; }
        catch (Result_J e) { return arr['J']; }
        catch (Result_K e) { return arr['K']; }
        catch (Result_L e) { return arr['L']; }
        catch (Result_M e) { return arr['M']; }
        catch (Result_N e) { return arr['N']; }
        catch (Result_O e) { return arr['O']; }
        catch (Result_P e) { return arr['P']; }
        catch (Result_Q e) { return arr['Q']; }
        catch (Result_R e) { return arr['R']; }
        catch (Result_S e) { return arr['S']; }
        catch (Result_T e) { return arr['T']; }
        catch (Result_U e) { return arr['U']; }
        catch (Result_V e) { return arr['V']; }
        catch (Result_W e) { return arr['W']; }
        catch (Result_X e) { return arr['X']; }
        catch (Result_Y e) { return arr['Y']; }
        catch (Result_Z e) { return arr['Z']; }
        catch (Result_a e) { return arr['a']; }
        catch (Result_b e) { return arr['b']; }
        catch (Result_c e) { return arr['c']; }
        catch (Result_d e) { return arr['d']; }
        catch (Result_e e) { return arr['e']; }
        catch (Result_f e) { return arr['f']; }
        catch (Exception e) {}
        return 0;
    }

    @Benchmark
    public int switchCase() {
        char value = getValue2();
        switch (value) {
            case 'A': return arr['A'];
            case 'B': return arr['B'];
            case 'C': return arr['C'];
            case 'D': return arr['D'];
            case 'E': return arr['E'];
            case 'F': return arr['F'];
            case 'G': return arr['G'];
            case 'H': return arr['H'];
            case 'I': return arr['I'];
            case 'J': return arr['J'];
            case 'K': return arr['K'];
            case 'L': return arr['L'];
            case 'M': return arr['M'];
            case 'N': return arr['N'];
            case 'O': return arr['O'];
            case 'P': return arr['P'];
            case 'Q': return arr['Q'];
            case 'R': return arr['R'];
            case 'S': return arr['S'];
            case 'T': return arr['T'];
            case 'U': return arr['U'];
            case 'V': return arr['V'];
            case 'W': return arr['W'];
            case 'X': return arr['X'];
            case 'Y': return arr['Y'];
            case 'Z': return arr['Z'];
            case 'a': return arr['a'];
            case 'b': return arr['b'];
            case 'c': return arr['c'];
            case 'd': return arr['d'];
            case 'e': return arr['e'];
            case 'f': return arr['f'];
            default: break;
        }
        return 0;
    }

}
