/*
 * Copyright (c) 2026, BELLSOFT. All rights reserved.
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @bug 8360517
 * @summary An index that repeats the trip counter one iteration late is replaced
 *          by an affine function of the trip counter. The index in question is the
 *          lastRet field of a scalarized ArrayList iterator, so the test drives the
 *          loops that actually own it: a foreach over an ArrayList, an explicit
 *          Iterator, and the shape they compile into, written out by hand.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver compiler.loopopts.TestForeachIndexes
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=dontinline,compiler.loopopts.TestForeachIndexes::deoptHere
 *                   compiler.loopopts.TestForeachIndexes deopt
 */
public class TestForeachIndexes {
    static final int MAX = Integer.MAX_VALUE;
    static final int MIN = Integer.MIN_VALUE;

    static final int SIZE = 64;
    static final ArrayList<Integer> LIST = new ArrayList<>();
    static {
        for (int i = 0; i < SIZE; i++) {
            LIST.add(i * 7);
        }
    }

    static final int MAX_TRIPS = 128;
    static final int[] TRACE = new int[MAX_TRIPS];

    // Ranges never wrap past the limit: that would be an endless loop in plain Java.
    static final int[][] FWD = {{0, 0}, {0, 1}, {0, 2}, {0, 17}, {7, 7}, {-3, 3}, {10, 0},
                                {MAX - 5, MAX}, {MAX - 1, MAX}, {MIN, MIN + 9}};
    static final int[][] BWD = {{0, 0}, {17, 0}, {3, 2}, {0, 10}, {MIN + 9, MIN}, {MIN + 1, MIN},
                                {MAX, MAX - 9}};

    // ------------------------------------------------------------------- model

    // The eliminated index holds the trip counter of the previous iteration plus a
    // constant, so its value at iteration n is a closed formula. Nothing here is a
    // loop, so nothing here can be transformed along with the code under test.
    static int lagging(int init, int stride, int k, int n) {
        return init + (n - 1) * stride + k;
    }

    static int trips(int init, int limit, int stride) {
        return (int) Math.max(0, Math.ceilDiv((long) limit - init, stride));
    }

    static void checkTrace(String name, int init, int limit, int stride, int k, int seen) {
        String at = name + "(" + init + ", " + limit + ")";
        Asserts.assertEQ(trips(init, limit, stride), seen, at + " trip count");
        for (int n = 0; n < seen; n++) {
            Asserts.assertEQ(lagging(init, stride, k, n), TRACE[n], at + " iteration " + n);
        }
    }

    // ------------------------------------------- the shape an iterator compiles into

    static final class Cursor {
        int cursor;
        int lastRet;
    }

    // Both range checks are dead, and C2 keeps them anyway; without them the field
    // does not survive as a phi and there is nothing to eliminate. This is what
    // ArrayList.Itr looks like once it is inlined and scalar replaced.
    @Test
    static int scan(int init, int limit) {
        Cursor c = new Cursor();
        c.cursor = init;
        c.lastRet = init - 1;
        int n = 0;
        while (c.cursor < limit) {
            if (c.cursor >= limit) { throw new NoSuchElementException(); }
            TRACE[n++] = c.lastRet;
            c.lastRet = c.cursor;
            c.cursor = c.cursor + 1;
        }
        return n;
    }

    @Run(test = "scan")
    static void runScan() {
        for (int[] r : FWD) {
            checkTrace("scan", r[0], r[1], 1, 0, scan(r[0], r[1]));
        }
    }

    // the index runs one iteration behind and MAX ahead: both the entry value and
    // the values inside the loop wrap around
    @Test
    static int scanOffsetMax(int init, int limit) {
        Cursor c = new Cursor();
        c.cursor = init;
        c.lastRet = init + (MAX - 1);
        int n = 0;
        while (c.cursor < limit) {
            if (c.cursor >= limit) { throw new NoSuchElementException(); }
            TRACE[n++] = c.lastRet;
            c.lastRet = c.cursor + MAX;
            c.cursor = c.cursor + 1;
        }
        return n;
    }

    @Run(test = "scanOffsetMax")
    static void runScanOffsetMax() {
        for (int[] r : FWD) {
            checkTrace("scanOffsetMax", r[0], r[1], 1, MAX, scanOffsetMax(r[0], r[1]));
        }
    }

    // the offset itself overflows: MIN - stride == MAX
    @Test
    static int scanOffsetMin(int init, int limit) {
        Cursor c = new Cursor();
        c.cursor = init;
        c.lastRet = init + MAX;
        int n = 0;
        while (c.cursor < limit) {
            if (c.cursor >= limit) { throw new NoSuchElementException(); }
            TRACE[n++] = c.lastRet;
            c.lastRet = c.cursor + MIN;
            c.cursor = c.cursor + 1;
        }
        return n;
    }

    @Run(test = "scanOffsetMin")
    static void runScanOffsetMin() {
        for (int[] r : FWD) {
            checkTrace("scanOffsetMin", r[0], r[1], 1, MIN, scanOffsetMin(r[0], r[1]));
        }
    }

    // backwards, with MAX - (-1) == MIN as the offset
    @Test
    static int scanBackwards(int init, int limit) {
        Cursor c = new Cursor();
        c.cursor = init;
        c.lastRet = init + MIN;
        int n = 0;
        while (c.cursor > limit) {
            if (c.cursor <= limit) { throw new NoSuchElementException(); }
            TRACE[n++] = c.lastRet;
            c.lastRet = c.cursor + MAX;
            c.cursor = c.cursor - 1;
        }
        return n;
    }

    @Run(test = "scanBackwards")
    static void runScanBackwards() {
        for (int[] r : BWD) {
            checkTrace("scanBackwards", r[0], r[1], -1, MAX, scanBackwards(r[0], r[1]));
        }
    }

    // The entry value does not repeat the counter, so the index has to stay a phi.
    // Its values must still be right, and they are not an affine function of the
    // counter on the first iteration.
    @Test
    static int scanForeignEntry(int init, int limit) {
        Cursor c = new Cursor();
        c.cursor = init;
        c.lastRet = 42;
        int n = 0;
        while (c.cursor < limit) {
            if (c.cursor >= limit) { throw new NoSuchElementException(); }
            TRACE[n++] = c.lastRet;
            c.lastRet = c.cursor;
            c.cursor = c.cursor + 1;
        }
        return n;
    }

    @Run(test = "scanForeignEntry")
    static void runScanForeignEntry() {
        for (int[] r : FWD) {
            int seen = scanForeignEntry(r[0], r[1]);
            Asserts.assertEQ(trips(r[0], r[1], 1), seen, "scanForeignEntry trip count");
            for (int n = 0; n < seen; n++) {
                Asserts.assertEQ(n == 0 ? 42 : lagging(r[0], 1, 0, n), TRACE[n],
                                 "scanForeignEntry(" + r[0] + ", " + r[1] + ") iteration " + n);
            }
        }
    }

    // ------------------------------------------------------------- the real thing

    // A wrong index would read the wrong element, so the element sequence is the
    // observable. The expected element is a closed formula, not a second loop.
    //
    // The phi count is the signal that lastRet was eliminated: with the second index
    // still in the loop there are five phis here, without it four. Measured equal in
    // all three scenarios below.
    @Test
    @IR(counts = {IRNode.PHI, "4"}, phase = CompilePhase.PHASEIDEALLOOP1)
    static int foreach() {
        int n = 0;
        for (int v : LIST) {
            TRACE[n++] = v;
        }
        return n;
    }

    @Run(test = "foreach")
    static void runForeach() {
        int seen = foreach();
        Asserts.assertEQ(SIZE, seen, "foreach size");
        for (int n = 0; n < seen; n++) {
            Asserts.assertEQ(n * 7, TRACE[n], "foreach element " + n);
        }
    }

    @Test
    static int explicitIterator() {
        int n = 0;
        for (Iterator<Integer> it = LIST.iterator(); it.hasNext(); ) {
            TRACE[n++] = it.next();
        }
        return n;
    }

    @Run(test = "explicitIterator")
    static void runExplicitIterator() {
        int seen = explicitIterator();
        Asserts.assertEQ(SIZE, seen, "explicitIterator size");
        for (int n = 0; n < seen; n++) {
            Asserts.assertEQ(n * 7, TRACE[n], "explicitIterator element " + n);
        }
    }

    // ---------------------------------------------------------- deoptimization

    static WhiteBox wb;
    static int deoptAt = -1;
    static int deoptedFrames;

    // kept out of line, see @run: the deoptimization must not depend on a branch
    // profile, otherwise -Xcomp compiles the cold path and nothing deoptimizes
    static void deoptHere(int i) {
        if (i == deoptAt) {
            // every compiled frame on this stack, the loop below among them
            deoptedFrames = wb.deoptimizeFrames(true);
        }
    }

    // The index is live across the call, that is across a safepoint carrying this
    // frame in its debug information. When the frame is deoptimized there, the
    // eliminated index has to be rematerialized from the trip counter, and the rest
    // of the loop runs in the interpreter off that value.
    static int scanDeopt(int init, int limit) {
        Cursor c = new Cursor();
        c.cursor = init;
        c.lastRet = init - 1;
        int n = 0;
        while (c.cursor < limit) {
            if (c.cursor >= limit) { throw new NoSuchElementException(); }
            deoptHere(c.cursor);
            TRACE[n++] = c.lastRet;
            c.lastRet = c.cursor;
            c.cursor = c.cursor + 1;
        }
        return n;
    }

    static void runDeoptTests() throws Exception {
        wb = WhiteBox.getWhiteBox();
        java.lang.reflect.Method m =
            TestForeachIndexes.class.getDeclaredMethod("scanDeopt", int.class, int.class);

        int init = 0, limit = 16;
        for (int at = init; at < limit; at++) {
            deoptAt = -1;
            for (int i = 0; i < 20_000 && !wb.isMethodCompiled(m); i++) {
                scanDeopt(init, limit);
            }
            Asserts.assertTrue(wb.isMethodCompiled(m), "scanDeopt is expected to be compiled");

            deoptAt = at;
            deoptedFrames = 0;
            checkTrace("scanDeopt@" + at, init, limit, 1, 0, scanDeopt(init, limit));
            Asserts.assertGT(deoptedFrames, 0, "no frame was deoptimized at " + at);
            Asserts.assertFalse(wb.isMethodCompiled(m), "scanDeopt was not deoptimized at " + at);
        }
        deoptAt = -1;
    }

    // ------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("deopt")) {
            runDeoptTests();
            return;
        }
        TestFramework framework = new TestFramework();
        framework.addScenarios(new Scenario(0),
                               new Scenario(1, "-XX:LoopMaxUnroll=1"),
                               new Scenario(2, "-XX:+UnlockDiagnosticVMOptions", "-XX:+StressIGVN",
                                            "-XX:+StressGCM", "-XX:+StressLCM"));
        framework.start();
    }
}
