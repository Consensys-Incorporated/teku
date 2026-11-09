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

package tech.pegasys.teku.kzg;

import java.util.List;

class DataColumnSidecarJson {
  private List<String> commitments;
  private List<CellWithIdJson> cellWithColumnIds;
  private List<String> proofs;

  public DataColumnSidecarJson() {}

  public List<String> getCommitments() {
    return commitments;
  }

  public void setCommitments(final List<String> commitments) {
    this.commitments = commitments;
  }

  public List<CellWithIdJson> getCellWithColumnIds() {
    return cellWithColumnIds;
  }

  public void setCellWithColumnIds(final List<CellWithIdJson> cellWithColumnIds) {
    this.cellWithColumnIds = cellWithColumnIds;
  }

  public List<String> getProofs() {
    return proofs;
  }

  public void setProofs(final List<String> proofs) {
    this.proofs = proofs;
  }

  public static class CellWithIdJson {
    private String cell;
    private Integer columnId;

    public CellWithIdJson() {}

    public String getCell() {
      return cell;
    }

    public void setCell(final String cell) {
      this.cell = cell;
    }

    public Integer getColumnId() {
      return columnId;
    }

    public void setColumnId(final Integer columnId) {
      this.columnId = columnId;
    }
  }
}
