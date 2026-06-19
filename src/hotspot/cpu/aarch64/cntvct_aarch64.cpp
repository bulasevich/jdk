/*
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
 *
 */

#include "cntvct_aarch64.hpp"
#include "runtime/atomicAccess.hpp"
#include "runtime/globals_extension.hpp"
#include "vm_version_aarch64.hpp"

DEBUG_ONLY(volatile int Cntvct::_initialized = 0;)
jlong Cntvct::_epoch = 0;

static inline jlong read_cntvctss() {
  uint64_t res;
  __asm__ volatile("mrs %0, s3_3_c14_c0_6" : "=r"(res)); // s3_3_c14_c0_6 is the numeric encoding of CNTVCTSS_EL0 for old GNU assemblers
  return (jlong)res;
}

jlong Cntvct::set_epoch() {
  assert(0 == _epoch, "invariant");
  _epoch = read_cntvctss();
  return _epoch;
}

static bool ergonomics() {
  if (Cntvct::is_supported()) {
    FLAG_SET_ERGO_IF_DEFAULT(UseFastUnorderedTimeStamps, true);
  } else if (UseFastUnorderedTimeStamps) {
    assert(!FLAG_IS_DEFAULT(UseFastUnorderedTimeStamps), "Unexpected default value");
    warning("Ignoring UseFastUnorderedTimeStamps, hardware does not support FEAT_ECV");
    FLAG_SET_ERGO(UseFastUnorderedTimeStamps, false);
  }
  return UseFastUnorderedTimeStamps;
}

bool Cntvct::initialize() {
  precond(AtomicAccess::xchg(&_initialized, 1) == 0);
  assert(0 == _epoch, "invariant");
  if (!ergonomics()) {
    return false;
  }
  set_epoch();
  return _epoch != 0;
}

bool Cntvct::is_supported() {
  return VM_Version::supports_ecv();
}

jlong Cntvct::frequency() {
  return 1000000000LL; // FEAT_ECV mandates 1 GHz
}

jlong Cntvct::elapsed_counter() {
  return read_cntvctss() - _epoch;
}

jlong Cntvct::epoch() {
  return _epoch;
}

jlong Cntvct::raw() {
  return read_cntvctss();
}

bool Cntvct::enabled() {
  static bool result = initialize();
  return result;
}
