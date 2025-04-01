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

// Benchmark compares NullPointerException handler vs null check
//
// Intel
//   NullPointerExceptionHandling.NPE         avgt    5      1.590     0.010  ns/op
//   NullPointerExceptionHandling.nullcheck   avgt    5      1.415     0.019  ns/op
// AARCH
//   NullPointerExceptionHandling.NPE         avgt    5      2.617     0.003  ns/op
//   NullPointerExceptionHandling.nullcheck   avgt    5      2.616     0.001  ns/op

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
public class NullPointerExceptionHandling {

    static final Integer[] data = {
        Integer.valueOf(1), null, Integer.valueOf(3),  null, Integer.valueOf(5),  null, Integer.valueOf(7),  null,
        Integer.valueOf(9), null, Integer.valueOf(11), null, Integer.valueOf(13), null, Integer.valueOf(15), null
    };
    // A global counter used to index into array.
    static int index = 0;

    @Benchmark
    public int NPE() {
        index = (index + 1) & 0xf;
        try {
            return data[index];
        } catch (NullPointerException npe) {
            return 0;
        }
    }

    @Benchmark
    public int nullcheck() {
        index = (index + 1) & 0xf;
        if (data[index] != null) {
            return data[index];
        } else {
            return 0;
        }
    }

}
