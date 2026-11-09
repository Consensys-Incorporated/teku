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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SystemSignalListener {
  private static final Logger LOG = LogManager.getLogger();

  /**
   * Registers an action to execute when a system specific signal to reload configuration is
   * received. Currently this only supports listening for SIGHUP events.
   *
   * <p>Note that reflection is used to access these classes as they are not a standard part of the
   * Java API and may not be available on all JVMs. A warning is logged if the listener cannot be
   * added.
   *
   * @param action the action to run when a reconfigure signal is received.
   */
  @SuppressWarnings("JavaReflectionInvocation")
  public static void registerReloadConfigListener(final Runnable action) {
    try {
      final Class<?> signalClass = Class.forName("sun.misc.Signal");
      final Class<?> signalHandlerClass = Class.forName("sun.misc.SignalHandler");
      final Constructor<?> constructor = signalClass.getConstructor(String.class);
      final Object hupSignal = constructor.newInstance("HUP");
      final Method handleMethod = signalClass.getMethod("handle", signalClass, signalHandlerClass);
      final Object handler =
          Proxy.newProxyInstance(
              SystemSignalListener.class.getClassLoader(),
              new Class<?>[] {signalHandlerClass},
              (proxy, method, args) -> {
                if (method.getName().equals("handle")) {
                  action.run();
                  return null;
                } else {
                  return method.invoke(action);
                }
              });
      handleMethod.invoke(null, hupSignal, handler);
    } catch (Throwable e) {
      LOG.warn(
          "Unable to register listener for SIGHUP events. Dynamic config reloading will not be supported.");
      LOG.debug("Failed to register listener for SIGHUP events.", e);
    }
  }
}
