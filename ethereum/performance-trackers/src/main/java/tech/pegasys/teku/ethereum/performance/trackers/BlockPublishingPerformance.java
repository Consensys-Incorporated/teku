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

package tech.pegasys.teku.ethereum.performance.trackers;

public interface BlockPublishingPerformance {
  String COMPLETE_LABEL = "complete";

  BlockPublishingPerformance NOOP =
      new BlockPublishingPerformance() {

        @Override
        public void complete() {}

        @Override
        public void builderGetPayload() {}

        @Override
        public void blobSidecarsPrepared() {}

        @Override
        public void blobSidecarsPublishingInitiated() {}

        @Override
        public void dataColumnSidecarsPublishingInitiated() {}

        @Override
        public void blockPublishingInitiated() {}

        @Override
        public void blockImportCompleted() {}

        @Override
        public void blobSidecarsImportCompleted() {}
      };

  void blobSidecarsPublishingInitiated();

  void dataColumnSidecarsPublishingInitiated();

  void blockPublishingInitiated();

  void builderGetPayload();

  void blobSidecarsPrepared();

  void blobSidecarsImportCompleted();

  void blockImportCompleted();

  void complete();
}
