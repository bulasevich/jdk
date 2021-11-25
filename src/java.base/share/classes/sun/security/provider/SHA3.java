/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.security.provider;

import jdk.internal.vm.annotation.IntrinsicCandidate;
import static sun.security.provider.ByteArrayAccess.*;
import java.nio.*;
import java.util.*;
import java.security.*;

/**
 * This class implements the Secure Hash Algorithm SHA-3 developed by
 * the National Institute of Standards and Technology along with the
 * National Security Agency as defined in FIPS PUB 202.
 *
 * <p>It implements java.security.MessageDigestSpi, and can be used
 * through Java Cryptography Architecture (JCA), as a pluggable
 * MessageDigest implementation.
 *
 * @since       9
 * @author      Valerie Peng
 */
abstract class SHA3 extends DigestBase {

    private static final int WIDTH = 200; // in bytes, e.g. 1600 bits
    private static final int DM = 5; // dimension of lanes

    private static final int NR = 24; // number of rounds

    // precomputed round constants needed by the step mapping Iota
    private static final long[] RC_CONSTANTS = {
        0x01L, 0x8082L, 0x800000000000808aL,
        0x8000000080008000L, 0x808bL, 0x80000001L,
        0x8000000080008081L, 0x8000000000008009L, 0x8aL,
        0x88L, 0x80008009L, 0x8000000aL,
        0x8000808bL, 0x800000000000008bL, 0x8000000000008089L,
        0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
        0x800aL, 0x800000008000000aL, 0x8000000080008081L,
        0x8000000000008080L, 0x80000001L, 0x8000000080008008L,
    };

    private final byte suffix;
    private byte[] state = new byte[WIDTH];
    private long[] lanes = new long[DM*DM];

    /**
     * Creates a new SHA-3 object.
     */
    SHA3(String name, int digestLength, byte suffix, int c) {
        super(name, digestLength, (WIDTH - c));
        this.suffix = suffix;
    }

    private void implCompressCheck(byte[] b, int ofs) {
        Objects.requireNonNull(b);
    }

    /**
     * Core compression function. Processes blockSize bytes at a time
     * and updates the state of this object.
     */
    void implCompress(byte[] b, int ofs) {
        implCompressCheck(b, ofs);
        implCompress0(b, ofs);
    }

    @IntrinsicCandidate
    private void implCompress0(byte[] b, int ofs) {
       for (int i = 0; i < buffer.length; i++) {
           state[i] ^= b[ofs++];
       }
       keccak();
    }

    /**
     * Return the digest. Subclasses do not need to reset() themselves,
     * DigestBase calls implReset() when necessary.
     */
    void implDigest(byte[] out, int ofs) {
        int numOfPadding =
            setPaddingBytes(suffix, buffer, (int)(bytesProcessed % buffer.length));
        if (numOfPadding < 1) {
            throw new ProviderException("Incorrect pad size: " + numOfPadding);
        }
        implCompress(buffer, 0);
        System.arraycopy(state, 0, out, ofs, engineGetDigestLength());
    }

    /**
     * Resets the internal state to start a new hash.
     */
    void implReset() {
        Arrays.fill(state, (byte)0);
        Arrays.fill(lanes, 0L);
    }

    /**
     * Utility function for padding the specified data based on the
     * pad10*1 algorithm (section 5.1) and the 2-bit suffix "01" required
     * for SHA-3 hash (section 6.1).
     */
    private static int setPaddingBytes(byte suffix, byte[] in, int len) {
        if (len != in.length) {
            // erase leftover values
            Arrays.fill(in, len, in.length, (byte)0);
            // directly store the padding bytes into the input
            // as the specified buffer is allocated w/ size = rateR
            in[len] |= suffix;
            in[in.length - 1] |= (byte) 0x80;
        }
        return (in.length - len);
    }

    /**
     * Utility function for transforming the specified byte array 's'
     * into array of lanes 'm' as defined in section 3.1.2.
     */
    private static void bytes2Lanes(byte[] s, long[] m) {
        int sOfs = 0;
        // Conversion traverses along x-axis before y-axis
        for (int y = 0; y < DM; y++, sOfs += 40) {
            b2lLittle(s, sOfs, m, DM*y, 40);
        }
    }

    /**
     * Utility function for transforming the specified array of
     * lanes 'm' into a byte array 's' as defined in section 3.1.3.
     */
    private static void lanes2Bytes(long[] m, byte[] s) {
        int sOfs = 0;
        // Conversion traverses along x-axis before y-axis
        for (int y = 0; y < DM; y++, sOfs += 40) {
            l2bLittle(m, DM*y, s, sOfs, 40);
        }
    }

    /**
     * Step mapping Theta as defined in section 3.2.1 .
     */
    private static long[] smTheta(long[] a) {
        long c0 = a[0]^a[5]^a[10]^a[15]^a[20];
        long c1 = a[1]^a[6]^a[11]^a[16]^a[21];
        long c2 = a[2]^a[7]^a[12]^a[17]^a[22];
        long c3 = a[3]^a[8]^a[13]^a[18]^a[23];
        long c4 = a[4]^a[9]^a[14]^a[19]^a[24];
        long d0 = c4 ^ Long.rotateLeft(c1, 1);
        long d1 = c0 ^ Long.rotateLeft(c2, 1);
        long d2 = c1 ^ Long.rotateLeft(c3, 1);
        long d3 = c2 ^ Long.rotateLeft(c4, 1);
        long d4 = c3 ^ Long.rotateLeft(c0, 1);
        for (int y = 0; y < a.length; y += DM) {
            a[y] ^= d0;
            a[y+1] ^= d1;
            a[y+2] ^= d2;
            a[y+3] ^= d3;
            a[y+4] ^= d4;
        }
        return a;
    }

    /**
     * Merged Step mapping Rho (section 3.2.2) and Pi (section 3.2.3).
     * for performance. Optimization is achieved by precalculating
     * shift constants for the following loop
     *   int xNext, yNext;
     *   for (int t = 0, x = 1, y = 0; t <= 23; t++, x = xNext, y = yNext) {
     *        int numberOfShift = ((t + 1)*(t + 2)/2) % 64;
     *        a[y][x] = Long.rotateLeft(a[y][x], numberOfShift);
     *        xNext = y;
     *        yNext = (2 * x + 3 * y) % DM;
     *   }
     * and with inplace permutation.
     */
    private static long[] smPiRho(long[] a) {
        long tmp = Long.rotateLeft(a[10], 3);
        a[10] = Long.rotateLeft(a[1], 1);
        a[1] = Long.rotateLeft(a[6], 44);
        a[6] = Long.rotateLeft(a[9], 20);
        a[9] = Long.rotateLeft(a[22], 61);
        a[22] = Long.rotateLeft(a[14], 39);
        a[14] = Long.rotateLeft(a[20], 18);
        a[20] = Long.rotateLeft(a[2], 62);
        a[2] = Long.rotateLeft(a[12], 43);
        a[12] = Long.rotateLeft(a[13], 25);
        a[13] = Long.rotateLeft(a[19], 8);
        a[19] = Long.rotateLeft(a[23], 56);
        a[23] = Long.rotateLeft(a[15], 41);
        a[15] = Long.rotateLeft(a[4], 27);
        a[4] = Long.rotateLeft(a[24], 14);
        a[24] = Long.rotateLeft(a[21], 2);
        a[21] = Long.rotateLeft(a[8], 55);
        a[8] = Long.rotateLeft(a[16], 45);
        a[16] = Long.rotateLeft(a[5], 36);
        a[5] = Long.rotateLeft(a[3], 28);
        a[3] = Long.rotateLeft(a[18], 21);
        a[18] = Long.rotateLeft(a[17], 15);
        a[17] = Long.rotateLeft(a[11], 10);
        a[11] = Long.rotateLeft(a[7], 6);
        a[7] = tmp;
        return a;
    }

    /**
     * Step mapping Chi as defined in section 3.2.4.
     */
    private static long[] smChi(long[] a) {
        for (int y = 0; y < a.length; y+=DM) {
            long ay0 = a[y];
            long ay1 = a[y+1];
            long ay2 = a[y+2];
            long ay3 = a[y+3];
            long ay4 = a[y+4];
            a[y] = ay0 ^ ((~ay1) & ay2);
            a[y+1] = ay1 ^ ((~ay2) & ay3);
            a[y+2] = ay2 ^ ((~ay3) & ay4);
            a[y+3] = ay3 ^ ((~ay4) & ay0);
            a[y+4] = ay4 ^ ((~ay0) & ay1);
        }
        return a;
    }

    /**
     * Step mapping Iota as defined in section 3.2.5.
     */
    private static long[] smIota(long[] a, int rndIndex) {
        a[0] ^= RC_CONSTANTS[rndIndex];
        return a;
    }

    /**
     * The function Keccak as defined in section 5.2 with
     * rate r = 1600 and capacity c = (digest length x 2).
     */
    private void keccak() {                       // Graviton   Xeon_8268
        // keccak_0_default();                    // 2764ms     1846ms
        // keccak_1_nativeimpl();                 // 1701ms     1649ms
        // keccak_2_inlined_unrolled();           // 1754ms     1499ms
        // keccak_3_inlined_unrolled_localvars(); // 1437ms     1261ms

        // Nov, 25
        //
           keccak_0_default();                    // 2751ms
        // keccak_2_inlined();                    // 2776ms
        // keccak_2_unrolled();                   // 2550ms
        // keccak_2_inlined_unrolled();           // 1754ms
    }

    private void keccak_0_default() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);
        // process the lanes through step mappings
        for (int ir = 0; ir < NR; ir++) {
            smIota(smChi(smPiRho(smTheta(lanes))), ir);
        }
        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    private static native void keccak0(long[] lanes);

    private void keccak_1_nativeimpl() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);
        // call the native method impl
        keccak0(lanes);
        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    private void keccak_2_inlined() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);
        long[] a = lanes;
        // process the lanes through step mappings
        for (int ir = 0; ir < NR; ir++) {
            long c0 = a[0]^a[5]^a[10]^a[15]^a[20];
            long c1 = a[1]^a[6]^a[11]^a[16]^a[21];
            long c2 = a[2]^a[7]^a[12]^a[17]^a[22];
            long c3 = a[3]^a[8]^a[13]^a[18]^a[23];
            long c4 = a[4]^a[9]^a[14]^a[19]^a[24];
            long d0 = c4 ^ Long.rotateLeft(c1, 1);
            long d1 = c0 ^ Long.rotateLeft(c2, 1);
            long d2 = c1 ^ Long.rotateLeft(c3, 1);
            long d3 = c2 ^ Long.rotateLeft(c4, 1);
            long d4 = c3 ^ Long.rotateLeft(c0, 1);
            for (int y = 0; y < a.length; y += DM) {
                a[y] ^= d0;
                a[y+1] ^= d1;
                a[y+2] ^= d2;
                a[y+3] ^= d3;
                a[y+4] ^= d4;
            }

            // smPiRho
            long tmp = Long.rotateLeft(a[10], 3);
            a[10] = Long.rotateLeft(a[1], 1);
            a[1] = Long.rotateLeft(a[6], 44);
            a[6] = Long.rotateLeft(a[9], 20);
            a[9] = Long.rotateLeft(a[22], 61);
            a[22] = Long.rotateLeft(a[14], 39);
            a[14] = Long.rotateLeft(a[20], 18);
            a[20] = Long.rotateLeft(a[2], 62);
            a[2] = Long.rotateLeft(a[12], 43);
            a[12] = Long.rotateLeft(a[13], 25);
            a[13] = Long.rotateLeft(a[19], 8);
            a[19] = Long.rotateLeft(a[23], 56);
            a[23] = Long.rotateLeft(a[15], 41);
            a[15] = Long.rotateLeft(a[4], 27);
            a[4] = Long.rotateLeft(a[24], 14);
            a[24] = Long.rotateLeft(a[21], 2);
            a[21] = Long.rotateLeft(a[8], 55);
            a[8] = Long.rotateLeft(a[16], 45);
            a[16] = Long.rotateLeft(a[5], 36);
            a[5] = Long.rotateLeft(a[3], 28);
            a[3] = Long.rotateLeft(a[18], 21);
            a[18] = Long.rotateLeft(a[17], 15);
            a[17] = Long.rotateLeft(a[11], 10);
            a[11] = Long.rotateLeft(a[7], 6);
            a[7] = tmp;

            // smChi
            for (int y = 0; y < a.length; y+=DM) {
                long ay0 = a[y];
                long ay1 = a[y+1];
                long ay2 = a[y+2];
                long ay3 = a[y+3];
                long ay4 = a[y+4];
                a[y] = ay0 ^ ((~ay1) & ay2);
                a[y+1] = ay1 ^ ((~ay2) & ay3);
                a[y+2] = ay2 ^ ((~ay3) & ay4);
                a[y+3] = ay3 ^ ((~ay4) & ay0);
                a[y+4] = ay4 ^ ((~ay0) & ay1);
            }

            // smIota
            a[0] ^= RC_CONSTANTS[ir];
        }
        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    private static long[] smTheta_unrolled(long[] a) {
        long c0 = a[0]^a[5]^a[10]^a[15]^a[20];
        long c1 = a[1]^a[6]^a[11]^a[16]^a[21];
        long c2 = a[2]^a[7]^a[12]^a[17]^a[22];
        long c3 = a[3]^a[8]^a[13]^a[18]^a[23];
        long c4 = a[4]^a[9]^a[14]^a[19]^a[24];
        long d0 = c4 ^ Long.rotateLeft(c1, 1);
        long d1 = c0 ^ Long.rotateLeft(c2, 1);
        long d2 = c1 ^ Long.rotateLeft(c3, 1);
        long d3 = c2 ^ Long.rotateLeft(c4, 1);
        long d4 = c3 ^ Long.rotateLeft(c0, 1);
        a[0] ^= d0;
        a[1] ^= d1;
        a[2] ^= d2;
        a[3] ^= d3;
        a[4] ^= d4;
        a[5] ^= d0;
        a[6] ^= d1;
        a[7] ^= d2;
        a[8] ^= d3;
        a[9] ^= d4;
        a[10] ^= d0;
        a[11] ^= d1;
        a[12] ^= d2;
        a[13] ^= d3;
        a[14] ^= d4;
        a[15] ^= d0;
        a[16] ^= d1;
        a[17] ^= d2;
        a[18] ^= d3;
        a[19] ^= d4;
        a[20] ^= d0;
        a[21] ^= d1;
        a[22] ^= d2;
        a[23] ^= d3;
        a[24] ^= d4;
        return a;
    }

    private static long[] smChi_unrolled(long[] a) {
        long ay0 = a[0];
        long ay1 = a[1];
        long ay2 = a[2];
        long ay3 = a[3];
        long ay4 = a[4];
        a[0] = ay0 ^ ((~ay1) & ay2);
        a[1] = ay1 ^ ((~ay2) & ay3);
        a[2] = ay2 ^ ((~ay3) & ay4);
        a[3] = ay3 ^ ((~ay4) & ay0);
        a[4] = ay4 ^ ((~ay0) & ay1);
        ay0 = a[5];
        ay1 = a[6];
        ay2 = a[7];
        ay3 = a[8];
        ay4 = a[9];
        a[5] = ay0 ^ ((~ay1) & ay2);
        a[6] = ay1 ^ ((~ay2) & ay3);
        a[7] = ay2 ^ ((~ay3) & ay4);
        a[8] = ay3 ^ ((~ay4) & ay0);
        a[9] = ay4 ^ ((~ay0) & ay1);
        ay0 = a[10];
        ay1 = a[11];
        ay2 = a[12];
        ay3 = a[13];
        ay4 = a[14];
        a[10] = ay0 ^ ((~ay1) & ay2);
        a[11] = ay1 ^ ((~ay2) & ay3);
        a[12] = ay2 ^ ((~ay3) & ay4);
        a[13] = ay3 ^ ((~ay4) & ay0);
        a[14] = ay4 ^ ((~ay0) & ay1);
        ay0 = a[15];
        ay1 = a[16];
        ay2 = a[17];
        ay3 = a[18];
        ay4 = a[19];
        a[15] = ay0 ^ ((~ay1) & ay2);
        a[16] = ay1 ^ ((~ay2) & ay3);
        a[17] = ay2 ^ ((~ay3) & ay4);
        a[18] = ay3 ^ ((~ay4) & ay0);
        a[19] = ay4 ^ ((~ay0) & ay1);
        ay0 = a[20];
        ay1 = a[21];
        ay2 = a[22];
        ay3 = a[23];
        ay4 = a[24];
        a[20] = ay0 ^ ((~ay1) & ay2);
        a[21] = ay1 ^ ((~ay2) & ay3);
        a[22] = ay2 ^ ((~ay3) & ay4);
        a[23] = ay3 ^ ((~ay4) & ay0);
        a[24] = ay4 ^ ((~ay0) & ay1);
        return a;
    }

    private void keccak_2_unrolled() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);
        // process the lanes through step mappings
        for (int ir = 0; ir < NR; ir++) {
            smIota(smChi_unrolled(smPiRho(smTheta_unrolled(lanes))), ir);
        }
        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    private void keccak_2_inlined_unrolled() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);
        long[] a = lanes;
        // process the lanes through step mappings
        for (int ir = 0; ir < NR; ir++) {
            long c0 = a[0]^a[5]^a[10]^a[15]^a[20];
            long c1 = a[1]^a[6]^a[11]^a[16]^a[21];
            long c2 = a[2]^a[7]^a[12]^a[17]^a[22];
            long c3 = a[3]^a[8]^a[13]^a[18]^a[23];
            long c4 = a[4]^a[9]^a[14]^a[19]^a[24];
            long d0 = c4 ^ Long.rotateLeft(c1, 1);
            long d1 = c0 ^ Long.rotateLeft(c2, 1);
            long d2 = c1 ^ Long.rotateLeft(c3, 1);
            long d3 = c2 ^ Long.rotateLeft(c4, 1);
            long d4 = c3 ^ Long.rotateLeft(c0, 1);
            a[0] ^= d0;
            a[1] ^= d1;
            a[2] ^= d2;
            a[3] ^= d3;
            a[4] ^= d4;
            a[5] ^= d0;
            a[6] ^= d1;
            a[7] ^= d2;
            a[8] ^= d3;
            a[9] ^= d4;
            a[10] ^= d0;
            a[11] ^= d1;
            a[12] ^= d2;
            a[13] ^= d3;
            a[14] ^= d4;
            a[15] ^= d0;
            a[16] ^= d1;
            a[17] ^= d2;
            a[18] ^= d3;
            a[19] ^= d4;
            a[20] ^= d0;
            a[21] ^= d1;
            a[22] ^= d2;
            a[23] ^= d3;
            a[24] ^= d4;

            // smPiRho
            long tmp = Long.rotateLeft(a[10], 3);
            a[10] = Long.rotateLeft(a[1], 1);
            a[1] = Long.rotateLeft(a[6], 44);
            a[6] = Long.rotateLeft(a[9], 20);
            a[9] = Long.rotateLeft(a[22], 61);
            a[22] = Long.rotateLeft(a[14], 39);
            a[14] = Long.rotateLeft(a[20], 18);
            a[20] = Long.rotateLeft(a[2], 62);
            a[2] = Long.rotateLeft(a[12], 43);
            a[12] = Long.rotateLeft(a[13], 25);
            a[13] = Long.rotateLeft(a[19], 8);
            a[19] = Long.rotateLeft(a[23], 56);
            a[23] = Long.rotateLeft(a[15], 41);
            a[15] = Long.rotateLeft(a[4], 27);
            a[4] = Long.rotateLeft(a[24], 14);
            a[24] = Long.rotateLeft(a[21], 2);
            a[21] = Long.rotateLeft(a[8], 55);
            a[8] = Long.rotateLeft(a[16], 45);
            a[16] = Long.rotateLeft(a[5], 36);
            a[5] = Long.rotateLeft(a[3], 28);
            a[3] = Long.rotateLeft(a[18], 21);
            a[18] = Long.rotateLeft(a[17], 15);
            a[17] = Long.rotateLeft(a[11], 10);
            a[11] = Long.rotateLeft(a[7], 6);
            a[7] = tmp;

            // smChi
            long ay0 = a[0];
            long ay1 = a[1];
            long ay2 = a[2];
            long ay3 = a[3];
            long ay4 = a[4];
            a[0] = ay0 ^ ((~ay1) & ay2);
            a[1] = ay1 ^ ((~ay2) & ay3);
            a[2] = ay2 ^ ((~ay3) & ay4);
            a[3] = ay3 ^ ((~ay4) & ay0);
            a[4] = ay4 ^ ((~ay0) & ay1);
            ay0 = a[5];
            ay1 = a[6];
            ay2 = a[7];
            ay3 = a[8];
            ay4 = a[9];
            a[5] = ay0 ^ ((~ay1) & ay2);
            a[6] = ay1 ^ ((~ay2) & ay3);
            a[7] = ay2 ^ ((~ay3) & ay4);
            a[8] = ay3 ^ ((~ay4) & ay0);
            a[9] = ay4 ^ ((~ay0) & ay1);
            ay0 = a[10];
            ay1 = a[11];
            ay2 = a[12];
            ay3 = a[13];
            ay4 = a[14];
            a[10] = ay0 ^ ((~ay1) & ay2);
            a[11] = ay1 ^ ((~ay2) & ay3);
            a[12] = ay2 ^ ((~ay3) & ay4);
            a[13] = ay3 ^ ((~ay4) & ay0);
            a[14] = ay4 ^ ((~ay0) & ay1);
            ay0 = a[15];
            ay1 = a[16];
            ay2 = a[17];
            ay3 = a[18];
            ay4 = a[19];
            a[15] = ay0 ^ ((~ay1) & ay2);
            a[16] = ay1 ^ ((~ay2) & ay3);
            a[17] = ay2 ^ ((~ay3) & ay4);
            a[18] = ay3 ^ ((~ay4) & ay0);
            a[19] = ay4 ^ ((~ay0) & ay1);
            ay0 = a[20];
            ay1 = a[21];
            ay2 = a[22];
            ay3 = a[23];
            ay4 = a[24];
            a[20] = ay0 ^ ((~ay1) & ay2);
            a[21] = ay1 ^ ((~ay2) & ay3);
            a[22] = ay2 ^ ((~ay3) & ay4);
            a[23] = ay3 ^ ((~ay4) & ay0);
            a[24] = ay4 ^ ((~ay0) & ay1);

            // smIota
            a[0] ^= RC_CONSTANTS[ir];
        }
        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    private void keccak_3_inlined_unrolled_localvars() {
        // convert the 200-byte state into 25 lanes
        bytes2Lanes(state, lanes);
        long a0 = lanes[0];
        long a1 = lanes[1];
        long a2 = lanes[2];
        long a3 = lanes[3];
        long a4 = lanes[4];
        long a5 = lanes[5];
        long a6 = lanes[6];
        long a7 = lanes[7];
        long a8 = lanes[8];
        long a9 = lanes[9];
        long a10 = lanes[10];
        long a11 = lanes[11];
        long a12 = lanes[12];
        long a13 = lanes[13];
        long a14 = lanes[14];
        long a15 = lanes[15];
        long a16 = lanes[16];
        long a17 = lanes[17];
        long a18 = lanes[18];
        long a19 = lanes[19];
        long a20 = lanes[20];
        long a21 = lanes[21];
        long a22 = lanes[22];
        long a23 = lanes[23];
        long a24 = lanes[24];

        // process the lanes through step mappings
        for (int ir = 0; ir < NR; ir++) {
            long c0 = a0^a5^a10^a15^a20;
            long c1 = a1^a6^a11^a16^a21;
            long c2 = a2^a7^a12^a17^a22;
            long c3 = a3^a8^a13^a18^a23;
            long c4 = a4^a9^a14^a19^a24;
            long d0 = c4 ^ Long.rotateLeft(c1, 1);
            long d1 = c0 ^ Long.rotateLeft(c2, 1);
            long d2 = c1 ^ Long.rotateLeft(c3, 1);
            long d3 = c2 ^ Long.rotateLeft(c4, 1);
            long d4 = c3 ^ Long.rotateLeft(c0, 1);
            a0 ^= d0;
            a1 ^= d1;
            a2 ^= d2;
            a3 ^= d3;
            a4 ^= d4;
            a5 ^= d0;
            a6 ^= d1;
            a7 ^= d2;
            a8 ^= d3;
            a9 ^= d4;
            a10 ^= d0;
            a11 ^= d1;
            a12 ^= d2;
            a13 ^= d3;
            a14 ^= d4;
            a15 ^= d0;
            a16 ^= d1;
            a17 ^= d2;
            a18 ^= d3;
            a19 ^= d4;
            a20 ^= d0;
            a21 ^= d1;
            a22 ^= d2;
            a23 ^= d3;
            a24 ^= d4;

            // smPiRho
            long tmp = Long.rotateLeft(a10, 3);
            a10 = Long.rotateLeft(a1, 1);
            a1 = Long.rotateLeft(a6, 44);
            a6 = Long.rotateLeft(a9, 20);
            a9 = Long.rotateLeft(a22, 61);
            a22 = Long.rotateLeft(a14, 39);
            a14 = Long.rotateLeft(a20, 18);
            a20 = Long.rotateLeft(a2, 62);
            a2 = Long.rotateLeft(a12, 43);
            a12 = Long.rotateLeft(a13, 25);
            a13 = Long.rotateLeft(a19, 8);
            a19 = Long.rotateLeft(a23, 56);
            a23 = Long.rotateLeft(a15, 41);
            a15 = Long.rotateLeft(a4, 27);
            a4 = Long.rotateLeft(a24, 14);
            a24 = Long.rotateLeft(a21, 2);
            a21 = Long.rotateLeft(a8, 55);
            a8 = Long.rotateLeft(a16, 45);
            a16 = Long.rotateLeft(a5, 36);
            a5 = Long.rotateLeft(a3, 28);
            a3 = Long.rotateLeft(a18, 21);
            a18 = Long.rotateLeft(a17, 15);
            a17 = Long.rotateLeft(a11, 10);
            a11 = Long.rotateLeft(a7, 6);
            a7 = tmp;

            // smChi
            long ay0 = a0;
            long ay1 = a1;
            long ay2 = a2;
            long ay3 = a3;
            long ay4 = a4;
            a0 = ay0 ^ ((~ay1) & ay2);
            a1 = ay1 ^ ((~ay2) & ay3);
            a2 = ay2 ^ ((~ay3) & ay4);
            a3 = ay3 ^ ((~ay4) & ay0);
            a4 = ay4 ^ ((~ay0) & ay1);
            ay0 = a5;
            ay1 = a6;
            ay2 = a7;
            ay3 = a8;
            ay4 = a9;
            a5 = ay0 ^ ((~ay1) & ay2);
            a6 = ay1 ^ ((~ay2) & ay3);
            a7 = ay2 ^ ((~ay3) & ay4);
            a8 = ay3 ^ ((~ay4) & ay0);
            a9 = ay4 ^ ((~ay0) & ay1);
            ay0 = a10;
            ay1 = a11;
            ay2 = a12;
            ay3 = a13;
            ay4 = a14;
            a10 = ay0 ^ ((~ay1) & ay2);
            a11 = ay1 ^ ((~ay2) & ay3);
            a12 = ay2 ^ ((~ay3) & ay4);
            a13 = ay3 ^ ((~ay4) & ay0);
            a14 = ay4 ^ ((~ay0) & ay1);
            ay0 = a15;
            ay1 = a16;
            ay2 = a17;
            ay3 = a18;
            ay4 = a19;
            a15 = ay0 ^ ((~ay1) & ay2);
            a16 = ay1 ^ ((~ay2) & ay3);
            a17 = ay2 ^ ((~ay3) & ay4);
            a18 = ay3 ^ ((~ay4) & ay0);
            a19 = ay4 ^ ((~ay0) & ay1);
            ay0 = a20;
            ay1 = a21;
            ay2 = a22;
            ay3 = a23;
            ay4 = a24;
            a20 = ay0 ^ ((~ay1) & ay2);
            a21 = ay1 ^ ((~ay2) & ay3);
            a22 = ay2 ^ ((~ay3) & ay4);
            a23 = ay3 ^ ((~ay4) & ay0);
            a24 = ay4 ^ ((~ay0) & ay1);

            // smIota
            a0 ^= RC_CONSTANTS[ir];
        }

        lanes[0] = a0;
        lanes[1] = a1;
        lanes[2] = a2;
        lanes[3] = a3;
        lanes[4] = a4;
        lanes[5] = a5;
        lanes[6] = a6;
        lanes[7] = a7;
        lanes[8] = a8;
        lanes[9] = a9;
        lanes[10] = a10;
        lanes[11] = a11;
        lanes[12] = a12;
        lanes[13] = a13;
        lanes[14] = a14;
        lanes[15] = a15;
        lanes[16] = a16;
        lanes[17] = a17;
        lanes[18] = a18;
        lanes[19] = a19;
        lanes[20] = a20;
        lanes[21] = a21;
        lanes[22] = a22;
        lanes[23] = a23;
        lanes[24] = a24;

        // convert the resulting 25 lanes back into 200-byte state
        lanes2Bytes(lanes, state);
    }

    public Object clone() throws CloneNotSupportedException {
        SHA3 copy = (SHA3) super.clone();
        copy.state = copy.state.clone();
        copy.lanes = new long[DM*DM];
        return copy;
    }

    /**
     * SHA3-224 implementation class.
     */
    public static final class SHA224 extends SHA3 {
        public SHA224() {
            super("SHA3-224", 28, (byte)0x06, 56);
        }
    }

    /**
     * SHA3-256 implementation class.
     */
    public static final class SHA256 extends SHA3 {
        public SHA256() {
            super("SHA3-256", 32, (byte)0x06, 64);
        }
    }

    /**
     * SHAs-384 implementation class.
     */
    public static final class SHA384 extends SHA3 {
        public SHA384() {
            super("SHA3-384", 48, (byte)0x06, 96);
        }
    }

    /**
     * SHA3-512 implementation class.
     */
    public static final class SHA512 extends SHA3 {
        public SHA512() {
            super("SHA3-512", 64, (byte)0x06, 128);
        }
    }
}
