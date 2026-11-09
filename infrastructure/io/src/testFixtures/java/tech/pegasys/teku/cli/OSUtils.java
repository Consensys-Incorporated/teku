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

package tech.pegasys.teku.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class OSUtils {

  public static final String CR = System.getProperty("line.separator");
  public static final String SLASH = System.getProperty("file.separator");
  public static final boolean IS_WIN =
      System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

  public static String toOSPath(final String nixPath) {
    if (IS_WIN) {
      String ret = nixPath.replace('/', '\\');
      if (nixPath.startsWith("/")) {
        ret = "C:" + ret;
      }
      return ret;
    } else {
      return nixPath;
    }
  }

  public static void makeNonReadable(final Path path) throws IOException {
    if (IS_WIN) {
      removeAslOwnerPermissions(path, Set.of(AclEntryPermission.READ_DATA));
    } else {
      Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OTHERS_EXECUTE));
    }
  }

  public static void makeNonWritable(final Path path) throws IOException {
    if (IS_WIN) {
      removeAslOwnerPermissions(path, Set.of(AclEntryPermission.WRITE_DATA));
    } else {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_EXECUTE);

      Files.setPosixFilePermissions(path, perms);
    }
  }

  private static void removeAslOwnerPermissions(
      final Path path, final Set<AclEntryPermission> permissions) throws IOException {
    AclFileAttributeView fileAttributeView =
        Files.getFileAttributeView(path, AclFileAttributeView.class);

    List<AclEntry> newAcl =
        fileAttributeView.getAcl().stream()
            .map(
                aclEntry -> {
                  try {
                    if (aclEntry.principal().equals(fileAttributeView.getOwner())) {
                      Set<AclEntryPermission> curPermissions =
                          new HashSet<>(aclEntry.permissions());
                      curPermissions.removeAll(permissions);
                      return AclEntry.newBuilder(aclEntry).setPermissions(curPermissions).build();
                    } else {
                      return aclEntry;
                    }

                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.toList());
    fileAttributeView.setAcl(newAcl);
  }
}
