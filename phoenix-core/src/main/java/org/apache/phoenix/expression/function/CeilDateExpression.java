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
package org.apache.phoenix.expression.function;

import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.phoenix.expression.Expression;
import org.apache.phoenix.expression.LiteralExpression;
import org.apache.phoenix.schema.tuple.Tuple;
import org.apache.phoenix.schema.types.PDataType;
import org.apache.phoenix.schema.types.PVarchar;
import org.apache.phoenix.schema.types.PDate;
import org.apache.phoenix.schema.types.PInteger;
import org.apache.phoenix.parse.FunctionParseNode.BuiltInFunction;
import org.apache.phoenix.parse.FunctionParseNode.Argument;
import org.apache.phoenix.parse.FunctionParseNode.FunctionClassType;

import com.google.common.collect.Lists;

/**
 * Class encapsulating ceil operation on {@link org.apache.phoenix.schema.types.PDataType#DATE}.
 *
 * @since 3.0.0
 */
@BuiltInFunction(name = CeilFunction.NAME,
        args = {
                @Argument(allowedTypes = {PDate.class}),
                @Argument(allowedTypes = {PVarchar.class, PInteger.class}, defaultValue = "null", isConstant = true),
                @Argument(allowedTypes = {PInteger.class}, defaultValue = "1", isConstant = true)
        },
        classType = FunctionClassType.DERIVED
)
public class CeilDateExpression extends RoundDateExpression {

    public CeilDateExpression() {
    }

    /**
     * @param timeUnit - unit of time to round up to.
     *                 Creates a {@link CeilDateExpression} with default multiplier of 1.
     */
    public static Expression create(Expression expr, TimeUnit timeUnit) throws SQLException {
        return create(expr, timeUnit, 1);
    }

    /**
     * @param timeUnit   - unit of time to round up to
     * @param multiplier - determines the roll up window size.
     *                   Create a {@link CeilDateExpression}.
     */
    public static Expression create(Expression expr, TimeUnit timeUnit, int multiplier) throws SQLException {
        Expression timeUnitExpr = getTimeUnitExpr(timeUnit);
        Expression defaultMultiplierExpr = getMultiplierExpr(multiplier);
        List<Expression> expressions = Lists.newArrayList(expr, timeUnitExpr, defaultMultiplierExpr);
        return CeilDateExpression.create(expressions);
    }

    public static Expression create(List<Expression> children) throws SQLException {
        Object timeUnitValue = ((LiteralExpression) children.get(1)).getValue();
        TimeUnit timeUnit = TimeUnit.getTimeUnit(timeUnitValue != null ? timeUnitValue.toString() : null);
        switch (timeUnit) {
            case WEEK:
                return new CeilWeekExpression(children);
            case MONTH:
                return new CeilMonthExpression(children);
            case YEAR:
                return new CeilYearExpression(children);
            default:
                return new CeilDateExpression(children);
        }

    }

    public CeilDateExpression(List<Expression> children) {
        super(children);
    }

    @Override
    protected long getRoundUpAmount() {
        return divBy - 1;
    }

    @Override
    public String getName() {
        return CeilFunction.NAME;
    }

    @Override
    public boolean evaluate(Tuple tuple, ImmutableBytesWritable ptr) {
        if (children.get(0).evaluate(tuple, ptr)) {
            if (ptr.getLength() == 0) {
                return true; // child evaluated to null
            }
            PDataType dataType = getDataType();
            long time = dataType.getCodec().decodeLong(ptr, children.get(0).getSortOrder());
            long value = roundTime(time);
            Date d = new Date(value);
            byte[] byteValue = dataType.toBytes(d);
            ptr.set(byteValue);
            return true;
        }
        return false;
    }

}
