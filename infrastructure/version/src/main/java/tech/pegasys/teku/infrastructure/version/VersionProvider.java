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

package tech.pegasys.teku.infrastructure.version;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

public class VersionProvider {
  public static final String ENV_XDG_DATA_HOME = "XDG_DATA_HOME";
  public static final String ENV_HOME = "HOME";
  public static final String CLIENT_IDENTITY = "teku";
  public static final String IMPLEMENTATION_VERSION = "v" + getImplementationVersion();
  public static final String VERSION =
      CLIENT_IDENTITY + "/" + IMPLEMENTATION_VERSION + "/" + detectOS() + "/" + detectJvm();
  public static final Optional<String> COMMIT_HASH = getCommitHash();

  public static String defaultStoragePath() {
    final String detectedOS = normalizeOS(normalize("os.name"));
    return defaultStoragePathForNormalizedOS(detectedOS, System.getenv());
  }

  private static String getImplementationVersion() {
    final String version = VersionProvider.class.getPackage().getImplementationVersion();
    return version != null ? version : "<Unknown>";
  }

  private static String detectOS() {
    final String detectedOS = normalizeOS(normalize("os.name"));
    final String detectedArch = normalizeArch(normalize("os.arch"));
    return detectedOS + '-' + detectedArch;
  }

  private static String detectJvm() {
    final String detectedVM = normalizeVM(normalize("java.vendor"), normalize("java.vm.name"));
    final String detectedJavaVersion = System.getProperty("java.specification.version");
    return detectedVM + "-java-" + detectedJavaVersion;
  }

  private static Optional<String> getCommitHash() {
    return getCommitHash(
        VersionProvider.class.getClassLoader().getResourceAsStream("git.properties"));
  }

  static Optional<String> getCommitHash(final InputStream gitPropertiesStream) {
    try (InputStream is = gitPropertiesStream) {
      if (is != null) {
        final Properties properties = new Properties();
        properties.load(is);
        return Optional.ofNullable(properties.getProperty("git.commit.id"));
      } else {
        return Optional.empty();
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  static String defaultStoragePathForNormalizedOS(
      final String detectedOS, final Map<String, String> env) {
    if (detectedOS.equals("osx")) {
      return env.get(ENV_HOME) + "/Library/teku";
    }
    String dataHome = env.get(ENV_XDG_DATA_HOME);
    if (StringUtils.isEmpty(dataHome)) {
      dataHome = env.get(ENV_HOME) + "/.local/share";
    }
    return dataHome + "/teku";
  }

  private static String normalizeOS(final String osName) {
    if (osName.startsWith("aix")) {
      return "aix";
    }
    if (osName.startsWith("hpux")) {
      return "hpux";
    }
    if (osName.startsWith("os400")) {
      // Avoid the names such as os4000
      if (osName.length() <= 5 || !Character.isDigit(osName.charAt(5))) {
        return "os400";
      }
    }
    if (osName.startsWith("linux")) {
      return "linux";
    }
    if (osName.startsWith("macosx") || osName.startsWith("osx")) {
      return "osx";
    }
    if (osName.startsWith("freebsd")) {
      return "freebsd";
    }
    if (osName.startsWith("openbsd")) {
      return "openbsd";
    }
    if (osName.startsWith("netbsd")) {
      return "netbsd";
    }
    if (osName.startsWith("solaris") || osName.startsWith("sunos")) {
      return "sunos";
    }

    return osName;
  }

  private static String normalizeArch(final String osArch) {
    if (osArch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
      return "x86_64";
    }
    if (osArch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
      return "x86_32";
    }
    if (osArch.matches("^(ia64w?|itanium64)$")) {
      return "itanium_64";
    }
    if ("ia64n".equals(osArch)) {
      return "itanium_32";
    }
    if (osArch.matches("^(sparc|sparc32)$")) {
      return "sparc_32";
    }
    if (osArch.matches("^(sparcv9|sparc64)$")) {
      return "sparc_64";
    }
    if (osArch.matches("^(arm|arm32)$")) {
      return "arm_32";
    }
    if ("aarch64".equals(osArch)) {
      return "aarch_64";
    }
    if (osArch.matches("^(mips|mips32)$")) {
      return "mips_32";
    }
    if (osArch.matches("^(mipsel|mips32el)$")) {
      return "mipsel_32";
    }
    if ("mips64".equals(osArch)) {
      return "mips_64";
    }
    if ("mips64el".equals(osArch)) {
      return "mipsel_64";
    }
    if (osArch.matches("^(ppc|ppc32)$")) {
      return "ppc_32";
    }
    if (osArch.matches("^(ppcle|ppc32le)$")) {
      return "ppcle_32";
    }
    if ("ppc64".equals(osArch)) {
      return "ppc_64";
    }
    if ("ppc64le".equals(osArch)) {
      return "ppcle_64";
    }
    if ("s390".equals(osArch)) {
      return "s390_32";
    }
    if ("s390x".equals(osArch)) {
      return "s390_64";
    }

    return osArch;
  }

  private static String normalizeVM(final String javaVendor, final String javaVmName) {
    if (javaVmName.contains("graalvm") || javaVendor.contains("graalvm")) {
      return "graalvm";
    }
    if (javaVendor.contains("oracle")) {
      if (javaVmName.contains("openjdk")) {
        return "oracle_openjdk";
      } else {
        return "oracle";
      }
    }
    if (javaVendor.contains("adoptopenjdk")) {
      return "adoptopenjdk";
    }
    if (javaVendor.contains("openj9")) {
      return "openj9";
    }
    if (javaVendor.contains("azul")) {
      if (javaVmName.contains("zing")) {
        return "zing";
      } else {
        return "zulu";
      }
    }
    if (javaVendor.contains("amazoncominc")) {
      return "corretto";
    }

    return "-" + javaVendor + "-" + javaVmName;
  }

  private static String normalize(final String value) {
    if (value == null) {
      return "";
    }
    return System.getProperty(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
  }
}
