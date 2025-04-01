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

// Benchmark implements Fibonacchi algorithm to go recursively into nested function calls
// - compares a trivial implementaton vs implementation that throws exceptions instead of return statement
// - a thrown exception is catched and rethrown by parent call
//
// Intel
//   Rethrow.fib_exceptions                   avgt    5  26802.875   1941.360  ns/op
//   Rethrow.fib_simple                       avgt    5     55.056      4.899  ns/op
// AARCH
//   Rethrow.fib_exceptions                   avgt    5  22544.764   218.926  ns/op
//   Rethrow.fib_simple                       avgt    5     31.389     0.039  ns/op


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
public class Rethrow {

    long fibonacci_simple(int num) {
        if (num == 0) return 0;
        if (num == 1) return 1;
        return fibonacci_simple(num - 1) + fibonacci_simple(num - 2); 
    }

    @Benchmark
    public long fib_simple() {
        return fibonacci_simple(7);
    }

    static class LongException extends Exception {
        public long value;
        public LongException(long value) { this.value = value; }
    }

    void fibonacci(int num) throws LongException {
        if (num == 0) throw new LongException(0);
        if (num == 1) throw new LongException(1);
        try {
            fibonacci(num - 1);
        } catch (LongException value1) {
            try {
                fibonacci(num - 2); 
            } catch (LongException value2) {
                value1.value += value2.value;
                throw value1;
            }
        }
    }

    @Benchmark
    public long fib_exceptions() {
        try {
            fibonacci(7);
        } catch (LongException ex) {
            return ex.value;
        }
        return 0;
    }
}
