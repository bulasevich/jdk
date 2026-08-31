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

import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8360517
 * @summary Eliminating a second index that repeats the trip counter one iteration
 *          late must not change results: compare compiled runs against the values
 *          the interpreter produced.
 * @library /test/lib /
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   compiler.loopopts.TestDoubledIndexCorrectness
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:-EliminateDoubledIndex
 *                   compiler.loopopts.TestDoubledIndexCorrectness
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:LoopMaxUnroll=1
 *                   compiler.loopopts.TestDoubledIndexCorrectness
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   compiler.loopopts.TestDoubledIndexCorrectness
 */
public class TestDoubledIndexCorrectness {
    static final int MAX = Integer.MAX_VALUE, MIN = Integer.MIN_VALUE;
    static final int[] arr = new int[64];

    // Ranges never wrap past the limit: that would be an endless loop in Java.
    static final int[][] FWD1 = {{0, 0}, {0, 1}, {0, 2}, {0, 10}, {5, 5}, {-3, 3},
        {MAX - 5, MAX}, {MAX - 1, MAX}, {MIN, MIN + 9}, {10, 0}, {0, -5}, {MIN + 9, MIN}};
    static final int[][] FWD3 = {{0, 0}, {0, 1}, {0, 10}, {-3, 3}, {MAX - 6, MAX}, {MIN, MIN + 9}, {10, 0}};
    static final int[][] BWD1 = {{0, 0}, {10, 0}, {3, 2}, {MIN + 9, MIN}, {MIN + 1, MIN}, {MAX, MAX - 9}, {0, 10}};
    static final int[][] BWD7 = {{0, 0}, {70, 0}, {MIN + 70, MIN}, {MAX, MAX - 70}, {0, 10}};

    static int stride1(int init, int limit) {
        int sum = 0, prev = init - 1;
        for (int i = init; i < limit; i += 1) { sum = sum * 31 + prev; prev = i; }
        return sum * 31 + prev;
    }

    static int stride3(int init, int limit) {
        int sum = 0, prev = init - 3;
        for (int i = init; i < limit; i += 3) { sum = sum * 31 + prev; prev = i; }
        return sum * 31 + prev;
    }

    static int strideNeg1(int init, int limit) {
        int sum = 0, prev = init + 1;
        for (int i = init; i > limit; i -= 1) { sum = sum * 31 + prev; prev = i; }
        return sum * 31 + prev;
    }

    static int strideNeg7(int init, int limit) {
        int sum = 0, prev = init + 7;
        for (int i = init; i > limit; i -= 7) { sum = sum * 31 + prev; prev = i; }
        return sum * 31 + prev;
    }

    // 'prev = iv + k' keeps 'prev == iv + (k - stride)'
    static int affine(int init, int limit) {
        int sum = 0, prev = init + 5 - 1;
        for (int i = init; i < limit; i += 1) { sum = sum * 31 + prev; prev = i + 5; }
        return sum * 31 + prev;
    }

    // the entry value does not repeat the counter: the phi must stay
    static int foreignEntry(int init, int limit) {
        int sum = 0, prev = 7;
        for (int i = init; i < limit; i += 1) { sum = sum * 31 + prev; prev = i; }
        return sum * 31 + prev;
    }

    static int nested(int init, int limit) {
        int sum = 0, oprev = init - 1;
        for (int i = init; i < limit; i++) {
            int iprev = -1;
            for (int j = 0; j < 4; j++) { sum = sum * 31 + iprev + oprev; iprev = j; }
            sum = sum * 7 + iprev;
            oprev = i;
        }
        return sum * 31 + oprev;
    }

    // the repeated index reaches an uncommon trap: it must survive deoptimization
    static int trapped(int init, int limit, boolean cold) {
        int sum = 0, prev = init - 1;
        for (int i = init; i < limit; i++) {
            if (cold) { return prev * 1000 + i; }
            sum = sum * 31 + prev + arr[i & 63];
            prev = i;
        }
        return sum * 31 + prev;
    }

    interface Loop { int run(int init, int limit); }

    static long hash(int[][] cases, Loop loop) {
        long h = 0;
        for (int[] c : cases) { h = h * 1000003 + loop.run(c[0], c[1]); }
        return h;
    }

    static void check(String name, int[][] cases, Loop loop) {
        long expected = hash(cases, loop);   // under -Xbatch: interpreted
        for (int r = 0; r < 20_000; r++) {
            loop.run(0, 20);
        }
        Asserts.assertEQ(expected, hash(cases, loop), name);
    }

    public static void main(String[] args) {
        check("stride1", FWD1, TestDoubledIndexCorrectness::stride1);
        check("stride3", FWD3, TestDoubledIndexCorrectness::stride3);
        check("strideNeg1", BWD1, TestDoubledIndexCorrectness::strideNeg1);
        check("strideNeg7", BWD7, TestDoubledIndexCorrectness::strideNeg7);
        check("affine", FWD1, TestDoubledIndexCorrectness::affine);
        check("foreignEntry", FWD1, TestDoubledIndexCorrectness::foreignEntry);
        check("nested", FWD1, TestDoubledIndexCorrectness::nested);
        check("trapped", FWD1, (init, limit) -> trapped(init, limit, false));

        // deoptimize with the repeated index live in the trap state, then run again
        Asserts.assertEQ(2003, trapped(3, 100, true), "trappedCold");
        check("trappedAgain", FWD1, (init, limit) -> trapped(init, limit, false));
    }
}
