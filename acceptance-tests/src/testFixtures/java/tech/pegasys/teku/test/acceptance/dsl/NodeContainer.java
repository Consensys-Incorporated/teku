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

package tech.pegasys.teku.test.acceptance.dsl;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import org.testcontainers.containers.GenericContainer;

public class NodeContainer extends GenericContainer<NodeContainer> {

  private final Map<File, String> tarballToExpand = new HashMap<>();

  public NodeContainer(final String dockerImageName) {
    super(dockerImageName);
  }

  public NodeContainer(final Future<String> image) {
    super(image);
  }

  @Override
  protected void containerIsCreated(final String containerId) {
    super.containerIsCreated(containerId);

    tarballToExpand.forEach(
        (tarball, targetPath) -> expandTarballToContainer(containerId, tarball, targetPath));
  }

  public NodeContainer withExpandedTarballToContainer(final File tarball, final String targetPath) {
    tarballToExpand.put(tarball, targetPath);
    return this;
  }

  public void expandTarballToContainer(final File tarball, final String targetPath) {
    expandTarballToContainer(getContainerId(), tarball, targetPath);
  }

  private void expandTarballToContainer(
      final String containerId, final File tarball, final String targetPath) {
    try {
      dockerClient
          .copyArchiveToContainerCmd(containerId)
          .withTarInputStream(Files.newInputStream(tarball.toPath()))
          .withRemotePath(targetPath)
          .exec();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
