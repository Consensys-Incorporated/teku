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

package tech.pegasys.teku.bls.impl.blst;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.impl.BLS12381;

/**
 * The BLST JNI classes use a static block to automatically load the native library. That means that
 * it loads as soon as the class is loaded which is hard to control.
 *
 * <p>So this class only refers to Blst classes via reflection to ensure just referencing this class
 * does not trigger those classes to load.
 */
public class BlstLoader {
  private static final Logger LOG = LogManager.getLogger();

  public static final Optional<BLS12381> INSTANCE = loadBlst();

  private static Optional<BLS12381> loadBlst() {
    try {
      // Trigger loading of native library
      Class.forName("supranational.blst.blstJNI");

      // Load actual implementation - *might* trigger loading but doesn't always
      final Class<?> blstClass = Class.forName("tech.pegasys.teku.bls.impl.blst.BlstBLS12381");
      return Optional.of((BLS12381) blstClass.getDeclaredConstructor().newInstance());
    } catch (final InstantiationException
        | ExceptionInInitializerError
        | InvocationTargetException
        | NoSuchMethodException
        | IllegalAccessException
        | ClassNotFoundException e) {
      LOG.error("Couldn't load native BLS library", e);
      return Optional.empty();
    }
  }
}
