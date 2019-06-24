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
package org.apache.phoenix.index.write;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.phoenix.index.table.HTableFactory;
import org.apache.phoenix.index.write.AbstractParallelWriterIndexCommitter;
import org.apache.phoenix.index.write.ParallelWriterIndexCommitter;

/**
 * Like the {@link ParallelWriterIndexCommitter}, but does not block
 *
 *
 */
public class LazyParallelWriterIndexCommitter extends AbstractParallelWriterIndexCommitter {

    // for testing
    public LazyParallelWriterIndexCommitter(String hbaseVersion) {
        super(hbaseVersion);
    }

    public LazyParallelWriterIndexCommitter() {
        super();
    }
}
