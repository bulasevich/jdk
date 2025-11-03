/*
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class UnstableIf {
    private static int iteration;
    private static char[] data;
    private static int max_index;

    @Setup
    public void setup() {
        max_index = 999_999;
        data = new char[max_index + 1];
        data[max_index] = 'a';
        iteration = 0;
    }

    // Run with: -XX:PerMethodRecompilationCutoff=2
    // Expect: on the third deoptimization C2 reaches the recompilation cutoff;
    //         the method is no longer recompiled or marked non-entrant,
    //         so each call repeatedly triggers the same [unstable_if trap -> deopt] again
    // Result: performance drops roughly 1:1000; deoptimizations can be seen with -Xlog:deoptimization=debug
    //
    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public int noActionDeoptStorm() {
        iteration = (iteration < max_index) ? iteration + 1 : 0;

        // First array iteration: profiling makes C2 believe this condition is always true,
        // so it compiles the false branch as an uncommon trap (action=reinterpret).
        // Later, when that branch actually executes, it triggers uncommon_trap, causing a deoptimization.
        if (data[iteration] == 'a') { data[iteration] = 'b'; return 1; }

        // At the end of the second iteration over the array we are going to fail with the same uncommon trap
        // (with the recompiled nmethod now).
        if (data[iteration] == 'b') { data[iteration] = 'c'; return 2; }

        // If C2 is tired of recompiling the method (force it with PerMethodRecompilationCutoff=2),
        // it can insert uncommon_trap with action=none, so the deoptimization does not hit recompilation.
        // If we force this case to execute often, it will cause a lot of deoptimizations.
        if (data[iteration] == 'c') { max_index = 1; data[max_index] = 'c'; return 4; }

        return 0;
    }

    public char[] data1 = new char[1_000_000];

    @Setup
    public void setup1() {
        iteration = 0;
        java.util.Arrays.fill(data1, (char) 0);
        max_index = 999_999;
        data1[max_index] = (char)1;
    }

    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    @Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
    //
    // Run with: -XX:PerMethodTrapLimit=300
    // Expect: ~200 unstable_if uncommon traps that trigger recompilations
    //         ~10 unstable_if uncommon traps that do not trigger recompilations
    //         (GraphKit switches to Action_none after PerMethodRecompilationCutoff / 2 + 1)
    //         The final IF switches execution to frequent deopts.
    //
    // How the benchmark works:
    // - Iterates over data1[0..999_999]; only the last element is initialized to a non-zero value, the rest are 0.
    // - C2 initially compiles never-taken branches as uncommon_trap nodes.
    // - On each full array pass, one new IF condition becomes true,
    //   causing the previous speculation to fail -> unstable_if trap -> deopt -> reinterpret -> recompile.
    //
    public int deoptManyBranches() {
        iteration = (iteration < max_index) ? iteration + 1 : 0;

        if (data1[iteration] == (char)1) { data1[iteration] = (char)2; return 0x1; }
        if (data1[iteration] == (char)2) { data1[iteration] = (char)3; return 0x2; }
        if (data1[iteration] == (char)3) { data1[iteration] = (char)4; return 0x3; }
        if (data1[iteration] == (char)4) { data1[iteration] = (char)5; return 0x4; }
        if (data1[iteration] == (char)5) { data1[iteration] = (char)6; return 0x5; }
        if (data1[iteration] == (char)6) { data1[iteration] = (char)7; return 0x6; }
        if (data1[iteration] == (char)7) { data1[iteration] = (char)8; return 0x7; }
        if (data1[iteration] == (char)8) { data1[iteration] = (char)9; return 0x8; }
        if (data1[iteration] == (char)9) { data1[iteration] = (char)10; return 0x9; }
        if (data1[iteration] == (char)10) { data1[iteration] = (char)11; return 0xa; }
        if (data1[iteration] == (char)11) { data1[iteration] = (char)12; return 0xb; }
        if (data1[iteration] == (char)12) { data1[iteration] = (char)13; return 0xc; }
        if (data1[iteration] == (char)13) { data1[iteration] = (char)14; return 0xd; }
        if (data1[iteration] == (char)14) { data1[iteration] = (char)15; return 0xe; }
        if (data1[iteration] == (char)15) { data1[iteration] = (char)16; return 0xf; }
        if (data1[iteration] == (char)16) { data1[iteration] = (char)17; return 0x10; }
        if (data1[iteration] == (char)17) { data1[iteration] = (char)18; return 0x11; }
        if (data1[iteration] == (char)18) { data1[iteration] = (char)19; return 0x12; }
        if (data1[iteration] == (char)19) { data1[iteration] = (char)20; return 0x13; }
        if (data1[iteration] == (char)20) { data1[iteration] = (char)21; return 0x14; }
        if (data1[iteration] == (char)21) { data1[iteration] = (char)22; return 0x15; }
        if (data1[iteration] == (char)22) { data1[iteration] = (char)23; return 0x16; }
        if (data1[iteration] == (char)23) { data1[iteration] = (char)24; return 0x17; }
        if (data1[iteration] == (char)24) { data1[iteration] = (char)25; return 0x18; }
        if (data1[iteration] == (char)25) { data1[iteration] = (char)26; return 0x19; }
        if (data1[iteration] == (char)26) { data1[iteration] = (char)27; return 0x1a; }
        if (data1[iteration] == (char)27) { data1[iteration] = (char)28; return 0x1b; }
        if (data1[iteration] == (char)28) { data1[iteration] = (char)29; return 0x1c; }
        if (data1[iteration] == (char)29) { data1[iteration] = (char)30; return 0x1d; }
        if (data1[iteration] == (char)30) { data1[iteration] = (char)31; return 0x1e; }
        if (data1[iteration] == (char)31) { data1[iteration] = (char)32; return 0x1f; }
        if (data1[iteration] == (char)32) { data1[iteration] = (char)33; return 0x20; }
        if (data1[iteration] == (char)33) { data1[iteration] = (char)34; return 0x21; }
        if (data1[iteration] == (char)34) { data1[iteration] = (char)35; return 0x22; }
        if (data1[iteration] == (char)35) { data1[iteration] = (char)36; return 0x23; }
        if (data1[iteration] == (char)36) { data1[iteration] = (char)37; return 0x24; }
        if (data1[iteration] == (char)37) { data1[iteration] = (char)38; return 0x25; }
        if (data1[iteration] == (char)38) { data1[iteration] = (char)39; return 0x26; }
        if (data1[iteration] == (char)39) { data1[iteration] = (char)40; return 0x27; }
        if (data1[iteration] == (char)40) { data1[iteration] = (char)41; return 0x28; }
        if (data1[iteration] == (char)41) { data1[iteration] = (char)42; return 0x29; }
        if (data1[iteration] == (char)42) { data1[iteration] = (char)43; return 0x2a; }
        if (data1[iteration] == (char)43) { data1[iteration] = (char)44; return 0x2b; }
        if (data1[iteration] == (char)44) { data1[iteration] = (char)45; return 0x2c; }
        if (data1[iteration] == (char)45) { data1[iteration] = (char)46; return 0x2d; }
        if (data1[iteration] == (char)46) { data1[iteration] = (char)47; return 0x2e; }
        if (data1[iteration] == (char)47) { data1[iteration] = (char)48; return 0x2f; }
        if (data1[iteration] == (char)48) { data1[iteration] = (char)49; return 0x30; }
        if (data1[iteration] == (char)49) { data1[iteration] = (char)50; return 0x31; }
        if (data1[iteration] == (char)50) { data1[iteration] = (char)51; return 0x32; }
        if (data1[iteration] == (char)51) { data1[iteration] = (char)52; return 0x33; }
        if (data1[iteration] == (char)52) { data1[iteration] = (char)53; return 0x34; }
        if (data1[iteration] == (char)53) { data1[iteration] = (char)54; return 0x35; }
        if (data1[iteration] == (char)54) { data1[iteration] = (char)55; return 0x36; }
        if (data1[iteration] == (char)55) { data1[iteration] = (char)56; return 0x37; }
        if (data1[iteration] == (char)56) { data1[iteration] = (char)57; return 0x38; }
        if (data1[iteration] == (char)57) { data1[iteration] = (char)58; return 0x39; }
        if (data1[iteration] == (char)58) { data1[iteration] = (char)59; return 0x3a; }
        if (data1[iteration] == (char)59) { data1[iteration] = (char)60; return 0x3b; }
        if (data1[iteration] == (char)60) { data1[iteration] = (char)61; return 0x3c; }
        if (data1[iteration] == (char)61) { data1[iteration] = (char)62; return 0x3d; }
        if (data1[iteration] == (char)62) { data1[iteration] = (char)63; return 0x3e; }
        if (data1[iteration] == (char)63) { data1[iteration] = (char)64; return 0x3f; }
        if (data1[iteration] == (char)64) { data1[iteration] = (char)65; return 0x40; }
        if (data1[iteration] == (char)65) { data1[iteration] = (char)66; return 0x41; }
        if (data1[iteration] == (char)66) { data1[iteration] = (char)67; return 0x42; }
        if (data1[iteration] == (char)67) { data1[iteration] = (char)68; return 0x43; }
        if (data1[iteration] == (char)68) { data1[iteration] = (char)69; return 0x44; }
        if (data1[iteration] == (char)69) { data1[iteration] = (char)70; return 0x45; }
        if (data1[iteration] == (char)70) { data1[iteration] = (char)71; return 0x46; }
        if (data1[iteration] == (char)71) { data1[iteration] = (char)72; return 0x47; }
        if (data1[iteration] == (char)72) { data1[iteration] = (char)73; return 0x48; }
        if (data1[iteration] == (char)73) { data1[iteration] = (char)74; return 0x49; }
        if (data1[iteration] == (char)74) { data1[iteration] = (char)75; return 0x4a; }
        if (data1[iteration] == (char)75) { data1[iteration] = (char)76; return 0x4b; }
        if (data1[iteration] == (char)76) { data1[iteration] = (char)77; return 0x4c; }
        if (data1[iteration] == (char)77) { data1[iteration] = (char)78; return 0x4d; }
        if (data1[iteration] == (char)78) { data1[iteration] = (char)79; return 0x4e; }
        if (data1[iteration] == (char)79) { data1[iteration] = (char)80; return 0x4f; }
        if (data1[iteration] == (char)80) { data1[iteration] = (char)81; return 0x50; }
        if (data1[iteration] == (char)81) { data1[iteration] = (char)82; return 0x51; }
        if (data1[iteration] == (char)82) { data1[iteration] = (char)83; return 0x52; }
        if (data1[iteration] == (char)83) { data1[iteration] = (char)84; return 0x53; }
        if (data1[iteration] == (char)84) { data1[iteration] = (char)85; return 0x54; }
        if (data1[iteration] == (char)85) { data1[iteration] = (char)86; return 0x55; }
        if (data1[iteration] == (char)86) { data1[iteration] = (char)87; return 0x56; }
        if (data1[iteration] == (char)87) { data1[iteration] = (char)88; return 0x57; }
        if (data1[iteration] == (char)88) { data1[iteration] = (char)89; return 0x58; }
        if (data1[iteration] == (char)89) { data1[iteration] = (char)90; return 0x59; }
        if (data1[iteration] == (char)90) { data1[iteration] = (char)91; return 0x5a; }
        if (data1[iteration] == (char)91) { data1[iteration] = (char)92; return 0x5b; }
        if (data1[iteration] == (char)92) { data1[iteration] = (char)93; return 0x5c; }
        if (data1[iteration] == (char)93) { data1[iteration] = (char)94; return 0x5d; }
        if (data1[iteration] == (char)94) { data1[iteration] = (char)95; return 0x5e; }
        if (data1[iteration] == (char)95) { data1[iteration] = (char)96; return 0x5f; }
        if (data1[iteration] == (char)96) { data1[iteration] = (char)97; return 0x60; }
        if (data1[iteration] == (char)97) { data1[iteration] = (char)98; return 0x61; }
        if (data1[iteration] == (char)98) { data1[iteration] = (char)99; return 0x62; }
        if (data1[iteration] == (char)99) { data1[iteration] = (char)100; return 0x63; }
        if (data1[iteration] == (char)100) { data1[iteration] = (char)101; return 0x64; }
        if (data1[iteration] == (char)101) { data1[iteration] = (char)102; return 0x65; }
        if (data1[iteration] == (char)102) { data1[iteration] = (char)103; return 0x66; }
        if (data1[iteration] == (char)103) { data1[iteration] = (char)104; return 0x67; }
        if (data1[iteration] == (char)104) { data1[iteration] = (char)105; return 0x68; }
        if (data1[iteration] == (char)105) { data1[iteration] = (char)106; return 0x69; }
        if (data1[iteration] == (char)106) { data1[iteration] = (char)107; return 0x6a; }
        if (data1[iteration] == (char)107) { data1[iteration] = (char)108; return 0x6b; }
        if (data1[iteration] == (char)108) { data1[iteration] = (char)109; return 0x6c; }
        if (data1[iteration] == (char)109) { data1[iteration] = (char)110; return 0x6d; }
        if (data1[iteration] == (char)110) { data1[iteration] = (char)111; return 0x6e; }
        if (data1[iteration] == (char)111) { data1[iteration] = (char)112; return 0x6f; }
        if (data1[iteration] == (char)112) { data1[iteration] = (char)113; return 0x70; }
        if (data1[iteration] == (char)113) { data1[iteration] = (char)114; return 0x71; }
        if (data1[iteration] == (char)114) { data1[iteration] = (char)115; return 0x72; }
        if (data1[iteration] == (char)115) { data1[iteration] = (char)116; return 0x73; }
        if (data1[iteration] == (char)116) { data1[iteration] = (char)117; return 0x74; }
        if (data1[iteration] == (char)117) { data1[iteration] = (char)118; return 0x75; }
        if (data1[iteration] == (char)118) { data1[iteration] = (char)119; return 0x76; }
        if (data1[iteration] == (char)119) { data1[iteration] = (char)120; return 0x77; }
        if (data1[iteration] == (char)120) { data1[iteration] = (char)121; return 0x78; }
        if (data1[iteration] == (char)121) { data1[iteration] = (char)122; return 0x79; }
        if (data1[iteration] == (char)122) { data1[iteration] = (char)123; return 0x7a; }
        if (data1[iteration] == (char)123) { data1[iteration] = (char)124; return 0x7b; }
        if (data1[iteration] == (char)124) { data1[iteration] = (char)125; return 0x7c; }
        if (data1[iteration] == (char)125) { data1[iteration] = (char)126; return 0x7d; }
        if (data1[iteration] == (char)126) { data1[iteration] = (char)127; return 0x7e; }
        if (data1[iteration] == (char)127) { data1[iteration] = (char)128; return 0x7f; }
        if (data1[iteration] == (char)128) { data1[iteration] = (char)129; return 0x80; }
        if (data1[iteration] == (char)129) { data1[iteration] = (char)130; return 0x81; }
        if (data1[iteration] == (char)130) { data1[iteration] = (char)131; return 0x82; }
        if (data1[iteration] == (char)131) { data1[iteration] = (char)132; return 0x83; }
        if (data1[iteration] == (char)132) { data1[iteration] = (char)133; return 0x84; }
        if (data1[iteration] == (char)133) { data1[iteration] = (char)134; return 0x85; }
        if (data1[iteration] == (char)134) { data1[iteration] = (char)135; return 0x86; }
        if (data1[iteration] == (char)135) { data1[iteration] = (char)136; return 0x87; }
        if (data1[iteration] == (char)136) { data1[iteration] = (char)137; return 0x88; }
        if (data1[iteration] == (char)137) { data1[iteration] = (char)138; return 0x89; }
        if (data1[iteration] == (char)138) { data1[iteration] = (char)139; return 0x8a; }
        if (data1[iteration] == (char)139) { data1[iteration] = (char)140; return 0x8b; }
        if (data1[iteration] == (char)140) { data1[iteration] = (char)141; return 0x8c; }
        if (data1[iteration] == (char)141) { data1[iteration] = (char)142; return 0x8d; }
        if (data1[iteration] == (char)142) { data1[iteration] = (char)143; return 0x8e; }
        if (data1[iteration] == (char)143) { data1[iteration] = (char)144; return 0x8f; }
        if (data1[iteration] == (char)144) { data1[iteration] = (char)145; return 0x90; }
        if (data1[iteration] == (char)145) { data1[iteration] = (char)146; return 0x91; }
        if (data1[iteration] == (char)146) { data1[iteration] = (char)147; return 0x92; }
        if (data1[iteration] == (char)147) { data1[iteration] = (char)148; return 0x93; }
        if (data1[iteration] == (char)148) { data1[iteration] = (char)149; return 0x94; }
        if (data1[iteration] == (char)149) { data1[iteration] = (char)150; return 0x95; }
        if (data1[iteration] == (char)150) { data1[iteration] = (char)151; return 0x96; }
        if (data1[iteration] == (char)151) { data1[iteration] = (char)152; return 0x97; }
        if (data1[iteration] == (char)152) { data1[iteration] = (char)153; return 0x98; }
        if (data1[iteration] == (char)153) { data1[iteration] = (char)154; return 0x99; }
        if (data1[iteration] == (char)154) { data1[iteration] = (char)155; return 0x9a; }
        if (data1[iteration] == (char)155) { data1[iteration] = (char)156; return 0x9b; }
        if (data1[iteration] == (char)156) { data1[iteration] = (char)157; return 0x9c; }
        if (data1[iteration] == (char)157) { data1[iteration] = (char)158; return 0x9d; }
        if (data1[iteration] == (char)158) { data1[iteration] = (char)159; return 0x9e; }
        if (data1[iteration] == (char)159) { data1[iteration] = (char)160; return 0x9f; }
        if (data1[iteration] == (char)160) { data1[iteration] = (char)161; return 0xa0; }
        if (data1[iteration] == (char)161) { data1[iteration] = (char)162; return 0xa1; }
        if (data1[iteration] == (char)162) { data1[iteration] = (char)163; return 0xa2; }
        if (data1[iteration] == (char)163) { data1[iteration] = (char)164; return 0xa3; }
        if (data1[iteration] == (char)164) { data1[iteration] = (char)165; return 0xa4; }
        if (data1[iteration] == (char)165) { data1[iteration] = (char)166; return 0xa5; }
        if (data1[iteration] == (char)166) { data1[iteration] = (char)167; return 0xa6; }
        if (data1[iteration] == (char)167) { data1[iteration] = (char)168; return 0xa7; }
        if (data1[iteration] == (char)168) { data1[iteration] = (char)169; return 0xa8; }
        if (data1[iteration] == (char)169) { data1[iteration] = (char)170; return 0xa9; }
        if (data1[iteration] == (char)170) { data1[iteration] = (char)171; return 0xaa; }
        if (data1[iteration] == (char)171) { data1[iteration] = (char)172; return 0xab; }
        if (data1[iteration] == (char)172) { data1[iteration] = (char)173; return 0xac; }
        if (data1[iteration] == (char)173) { data1[iteration] = (char)174; return 0xad; }
        if (data1[iteration] == (char)174) { data1[iteration] = (char)175; return 0xae; }
        if (data1[iteration] == (char)175) { data1[iteration] = (char)176; return 0xaf; }
        if (data1[iteration] == (char)176) { data1[iteration] = (char)177; return 0xb0; }
        if (data1[iteration] == (char)177) { data1[iteration] = (char)178; return 0xb1; }
        if (data1[iteration] == (char)178) { data1[iteration] = (char)179; return 0xb2; }
        if (data1[iteration] == (char)179) { data1[iteration] = (char)180; return 0xb3; }
        if (data1[iteration] == (char)180) { data1[iteration] = (char)181; return 0xb4; }
        if (data1[iteration] == (char)181) { data1[iteration] = (char)182; return 0xb5; }
        if (data1[iteration] == (char)182) { data1[iteration] = (char)183; return 0xb6; }
        if (data1[iteration] == (char)183) { data1[iteration] = (char)184; return 0xb7; }
        if (data1[iteration] == (char)184) { data1[iteration] = (char)185; return 0xb8; }
        if (data1[iteration] == (char)185) { data1[iteration] = (char)186; return 0xb9; }
        if (data1[iteration] == (char)186) { data1[iteration] = (char)187; return 0xba; }
        if (data1[iteration] == (char)187) { data1[iteration] = (char)188; return 0xbb; }
        if (data1[iteration] == (char)188) { data1[iteration] = (char)189; return 0xbc; }
        if (data1[iteration] == (char)189) { data1[iteration] = (char)190; return 0xbd; }
        if (data1[iteration] == (char)190) { data1[iteration] = (char)191; return 0xbe; }
        if (data1[iteration] == (char)191) { data1[iteration] = (char)192; return 0xbf; }
        if (data1[iteration] == (char)192) { data1[iteration] = (char)193; return 0xc0; }
        if (data1[iteration] == (char)193) { data1[iteration] = (char)194; return 0xc1; }
        if (data1[iteration] == (char)194) { data1[iteration] = (char)195; return 0xc2; }
        if (data1[iteration] == (char)195) { data1[iteration] = (char)196; return 0xc3; }
        if (data1[iteration] == (char)196) { data1[iteration] = (char)197; return 0xc4; }
        if (data1[iteration] == (char)197) { data1[iteration] = (char)198; return 0xc5; }
        if (data1[iteration] == (char)198) { data1[iteration] = (char)199; return 0xc6; }
        if (data1[iteration] == (char)199) { data1[iteration] = (char)200; return 0xc7; }
        if (data1[iteration] == (char)200) { data1[iteration] = (char)201; return 0xc8; }
        if (data1[iteration] == (char)201) { data1[iteration] = (char)202; return 0xc9; }
        if (data1[iteration] == (char)202) { data1[iteration] = (char)203; return 0xca; }
        if (data1[iteration] == (char)203) { data1[iteration] = (char)204; return 0xcb; }
        if (data1[iteration] == (char)204) { data1[iteration] = (char)205; return 0xcc; }
        if (data1[iteration] == (char)205) { data1[iteration] = (char)206; return 0xcd; }
        if (data1[iteration] == (char)206) { data1[iteration] = (char)207; return 0xce; }
        if (data1[iteration] == (char)207) { data1[iteration] = (char)208; return 0xcf; }
        if (data1[iteration] == (char)208) { data1[iteration] = (char)209; return 0xd0; }
        if (data1[iteration] == (char)209) { data1[iteration] = (char)210; return 0xd1; }
        if (data1[iteration] == (char)210) {
            // Switch to short array iteration to make this branch frequently executed
            max_index = 1;
            data1[max_index] = (char)210;
        }

        return 0;
    }
}