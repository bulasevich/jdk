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

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

import java.util.ArrayList;

/*
 * @test
 * @bug 8360517
 * @summary A value that repeats the induction variable one iteration late must not
 *          be carried in a phi of its own: the loop then holds two indices at once.
 * @library /test/lib /
 * @run driver compiler.loopopts.TestEliminateDoubledIndex
 */
public class TestEliminateDoubledIndex {
    private static final int LEN = 512;
    private static final ArrayList<Object> list = new ArrayList<>();

    static {
        for (int i = 0; i < LEN; i++) {
            list.add(Integer.valueOf(i));
        }
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(
            new Scenario(0, "-XX:LoopMaxUnroll=1", "-XX:-UseOnStackReplacement"),
            new Scenario(1, "-XX:LoopMaxUnroll=1", "-XX:-UseOnStackReplacement",
                            "-XX:-EliminateDoubledIndex"));
        framework.start();
    }

    // 'prev' repeats 'i' one iteration late, so only 'i' and 'acc' need a phi.
    @Test
    @IR(applyIf = {"EliminateDoubledIndex", "true"}, counts = {IRNode.PHI, "2"}, phase = CompilePhase.PHASEIDEALLOOP1)
    @IR(applyIf = {"EliminateDoubledIndex", "false"}, counts = {IRNode.PHI, "3"}, phase = CompilePhase.PHASEIDEALLOOP1)
    public static int doubledIndexInLoop() {
        int prev = -1, acc = 0;
        for (int i = 0; i < LEN; i++) {
            acc += prev;
            prev = i;
        }
        return acc;
    }

    // The iterator's 'cursor' becomes the trip counter and 'lastRet' repeats it one
    // iteration late.
    @Test
    @IR(applyIf = {"EliminateDoubledIndex", "true"}, counts = {IRNode.PHI, "2"}, phase = CompilePhase.PHASEIDEALLOOP1)
    @IR(applyIf = {"EliminateDoubledIndex", "false"}, counts = {IRNode.PHI, "3"}, phase = CompilePhase.PHASEIDEALLOOP1)
    public static int foreach() {
        int n = 0;
        for (Object o : list) {
            n++;
        }
        return n;
    }

    // 'prev' equals 'i - 1' from the second iteration on, but not on the first one,
    // so the phi has to stay.
    @Test
    @IR(counts = {IRNode.PHI, "3"}, phase = CompilePhase.PHASEIDEALLOOP1)
    public static int foreignEntryValue() {
        int prev = 7, acc = 0;
        for (int i = 0; i < LEN; i++) {
            acc += prev;
            prev = i;
        }
        return acc;
    }

    @Check(test = "doubledIndexInLoop")
    public void checkLagInLoop(int result) {
        Asserts.assertEQ(result, LEN * (LEN - 1) / 2 - LEN);
    }

    @Check(test = "foreach")
    public void checkForeach(int result) {
        Asserts.assertEQ(result, LEN);
    }

    @Check(test = "foreignEntryValue")
    public void checkLagWithForeignEntryValue(int result) {
        Asserts.assertEQ(result, LEN * (LEN - 1) / 2 - (LEN - 1) + 7);
    }
}
