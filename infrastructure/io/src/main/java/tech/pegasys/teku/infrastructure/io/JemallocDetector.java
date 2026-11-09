/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.io;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SuppressWarnings("JavaCase")
public class JemallocDetector {
  private static final Logger LOG = LogManager.getLogger();

  @SuppressWarnings("NonFinalStaticField")
  private static String _jemalloc;

  public static void logJemallocPresence() {
    try {
      getJemalloc();
      if (_jemalloc != null) {
        LOG.info("jemalloc: " + _jemalloc);
      }
    } catch (final Throwable throwable) {
      LOG.info("jemalloc library not found.");
    }
  }

  private static void getJemalloc() {
    interface JemallocLib extends Library {
      int mallctl(
          String property,
          PointerByReference value,
          IntByReference len,
          String newValue,
          int newLen);
    }

    final JemallocLib jemallocLib = Native.load("jemalloc", JemallocLib.class);

    PointerByReference pVersion = new PointerByReference();
    IntByReference pSize = new IntByReference(Native.POINTER_SIZE);
    jemallocLib.mallctl("version", pVersion, pSize, null, 0);

    _jemalloc = pVersion.getValue().getString(0);
  }
}
