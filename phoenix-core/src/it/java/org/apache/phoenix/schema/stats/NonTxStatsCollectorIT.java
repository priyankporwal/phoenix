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
package org.apache.phoenix.schema.stats;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized.Parameters;

public class NonTxStatsCollectorIT extends BaseStatsCollectorIT {

    public NonTxStatsCollectorIT(boolean mutable,
                                 String transactionProvider, boolean columnEncoded) {
        super(mutable, transactionProvider, columnEncoded);
    }

    @Parameters(name = "mutable={0},transactionProvider={1},columnEncoded={2}")
    public static Collection<Object[]> provideData() {
        return Arrays.asList(
                new Object[][] {
                        // Immutable, Column Encoded
                        {false, null, true},
                        // Mutable, Column Encoded
                        {true, null, true},
                        // Immutable, Not Column Encoded
                        {false, null, false}
                }
        );
    }

}
