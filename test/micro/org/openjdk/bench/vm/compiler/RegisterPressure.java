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
 
package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import java.util.concurrent.TimeUnit;

/**
 * Register pressure test. This code circulates data among 31 variables to produce register pressure
 * On ARM64 the opto compiler uses 27 out of 31 available aarch registers (rscratch1, rscratch2 and rthread are reserved) which causes register spilling.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3)
@State(Scope.Thread)
public class RegisterPressure {
  long arr[];
  @Setup
  public void setup() {
    arr = new long[31];
  }
  @Benchmark
  public void test() {
    long v0 = arr[0]; long v1 = arr[1]; long v2 = arr[2]; long v3 = arr[3]; long v4 = arr[4];
    long v5 = arr[5]; long v6 = arr[6]; long v7 = arr[7]; long v8 = arr[8]; long v9 = arr[9];
    long vA = arr[10]; long vB = arr[11]; long vC = arr[12]; long vD = arr[13];
    long vE = arr[14]; long vF = arr[15]; long vG = arr[16]; long vH = arr[17];
    long vI = arr[18]; long vJ = arr[19]; long vK = arr[20]; long vL = arr[21];
    long vM = arr[22]; long vN = arr[23]; long vO = arr[24]; long vP = arr[25];
    long vQ = arr[26]; long vR = arr[27]; long vS = arr[28]; long vT = arr[29]; long vU = arr[30];
    
    v1 += v0; v2 += v1; v3 += v2; v4 += v3; v5 += v4; v6 += v5; v7 += v6; v8 += v7;
    v9 += v8; vA += v9; vB += vA; vC += vB; vD += vC; vE += vD; vF += vE; vG += vF;
    vH += vG; vI += vH; vJ += vI; vK += vJ; vL += vK; vM += vL; vN += vM; vO += vN;
    vP += vO; vQ += vP; vR += vQ; vS += vR; vT += vS; vU += vT; v0 += vU; 
    
    v1 *= v0; v2 *= v1; v3 *= v2; v4 *= v3; v5 *= v4; v6 *= v5; v7 *= v6; v8 *= v7;
    v9 *= v8; vA *= v9; vB *= vA; vC *= vB; vD *= vC; vE *= vD; vF *= vE; vG *= vF;
    vH *= vG; vI *= vH; vJ *= vI; vK *= vJ; vL *= vK; vM *= vL; vN *= vM; vO *= vN;
    vP *= vO; vQ *= vP; vR *= vQ; vS *= vR; vT *= vS; vU *= vT; v0 *= vU;

    v1 += v0; v2 += v1; v3 += v2; v4 += v3; v5 += v4; v6 += v5; v7 += v6; v8 += v7;
    v9 += v8; vA += v9; vB += vA; vC += vB; vD += vC; vE += vD; vF += vE; vG += vF;
    vH += vG; vI += vH; vJ += vI; vK += vJ; vL += vK; vM += vL; vN += vM; vO += vN;
    vP += vO; vQ += vP; vR += vQ; vS += vR; vT += vS; vU += vT; v0 += vU;

    v1 *= v0; v2 *= v1; v3 *= v2; v4 *= v3; v5 *= v4; v6 *= v5; v7 *= v6; v8 *= v7;
    v9 *= v8; vA *= v9; vB *= vA; vC *= vB; vD *= vC; vE *= vD; vF *= vE; vG *= vF;
    vH *= vG; vI *= vH; vJ *= vI; vK *= vJ; vL *= vK; vM *= vL; vN *= vM; vO *= vN;
    vP *= vO; vQ *= vP; vR *= vQ; vS *= vR; vT *= vS; vU *= vT; v0 *= vU;

    arr[0] = v0; arr[1] = v1; arr[2] = v2; arr[3] = v3; arr[4] = v4; arr[5] = v5; arr[6] = v6; 
    arr[7] = v7; arr[8] = v8; arr[9] = v9; arr[10] = vA; arr[11] = vB; arr[12] = vC;
    arr[13] = vD; arr[14] = vE; arr[15] = vF; arr[16] = vG; arr[17] = vH; arr[18] = vI;
    arr[19] = vJ; arr[20] = vK; arr[21] = vL; arr[22] = vM; arr[23] = vN; arr[24] = vO;
    arr[25] = vP; arr[26] = vQ; arr[27] = vR; arr[28] = vS; arr[29] = vT; arr[30] = vU;
  }
}

