/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.index.covered;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.phoenix.index.covered.LocalTableState;
import org.apache.phoenix.index.covered.update.ColumnReference;

/**
 * Manage a set of {@link org.apache.phoenix.index.covered.update.ColumnReference}s for the {@link LocalTableState}.
 */
public class CoveredColumns {

  Set<org.apache.phoenix.index.covered.update.ColumnReference> columns = new HashSet<org.apache.phoenix.index.covered.update.ColumnReference>();

  public Collection<? extends org.apache.phoenix.index.covered.update.ColumnReference> findNonCoveredColumns(
      Collection<? extends org.apache.phoenix.index.covered.update.ColumnReference> columns2) {
    List<org.apache.phoenix.index.covered.update.ColumnReference> uncovered = new ArrayList<org.apache.phoenix.index.covered.update.ColumnReference>();
    for (org.apache.phoenix.index.covered.update.ColumnReference column : columns2) {
      if (!columns.contains(column)) {
        uncovered.add(column);
      }
    }
    return uncovered;
  }

  public void addColumn(org.apache.phoenix.index.covered.update.ColumnReference column) {
    this.columns.add(column);
  }
}
