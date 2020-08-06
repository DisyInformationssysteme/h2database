/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.expression.function;

import java.util.Arrays;

import org.h2.api.ErrorCode;
import org.h2.engine.SessionLocal;
import org.h2.expression.Expression;
import org.h2.expression.TypedValueExpression;
import org.h2.message.DbException;
import org.h2.value.DataType;
import org.h2.value.TypeInfo;
import org.h2.value.Value;
import org.h2.value.ValueNull;
import org.h2.value.ValueVarbinary;
import org.h2.value.ValueVarchar;

/**
 * A SUBSTRING function.
 */
public final class SubstringFunction extends FunctionN {

    public SubstringFunction() {
        super(new Expression[3]);
    }

    @Override
    public Value getValue(SessionLocal session) {
        Value stringValue = args[0].getValue(session);
        if (stringValue == ValueNull.INSTANCE) {
            return ValueNull.INSTANCE;
        }
        Value startValue = args[1].getValue(session);
        if (startValue == ValueNull.INSTANCE) {
            return ValueNull.INSTANCE;
        }
        Value lengthValue;
        if (args.length >= 3) {
            lengthValue = args[2].getValue(session);
            if (lengthValue == ValueNull.INSTANCE) {
                return ValueNull.INSTANCE;
            }
        } else {
            lengthValue = null;
        }
        if (type.getValueType() == Value.VARBINARY) {
            byte[] s = stringValue.getBytesNoCopy();
            int sl = s.length;
            int start = startValue.getInt();
            // These compatibility conditions violate the Standard
            if (start == 0) {
                start = 1;
            } else if (start < 0) {
                start = sl + start + 1;
            }
            int end = lengthValue == null ? Math.max(sl + 1, start) : start + lengthValue.getInt();
            // SQL Standard requires "data exception - substring error" when
            // end < start but H2 does not throw it for compatibility
            start = Math.max(start, 1);
            end = Math.min(end, sl + 1);
            if (start > sl || end <= start) {
                return ValueVarbinary.EMPTY;
            }
            start--;
            end--;
            if (start == 0 && end == s.length) {
                return stringValue.convertTo(TypeInfo.TYPE_VARBINARY);
            }
            return ValueVarbinary.getNoCopy(Arrays.copyOfRange(s, start, end));
        } else {
            String s = stringValue.getString();
            int sl = s.length();
            int start = startValue.getInt();
            // These compatibility conditions violate the Standard
            if (start == 0) {
                start = 1;
            } else if (start < 0) {
                start = sl + start + 1;
            }
            int end = lengthValue == null ? Math.max(sl + 1, start) : start + lengthValue.getInt();
            // SQL Standard requires "data exception - substring error" when
            // end < start but H2 does not throw it for compatibility
            start = Math.max(start, 1);
            end = Math.min(end, sl + 1);
            if (start > sl || end <= start) {
                return session.getMode().treatEmptyStringsAsNull ? ValueNull.INSTANCE : ValueVarchar.EMPTY;
            }
            return ValueVarchar.get(s.substring(start - 1, end - 1), null);
        }
    }

    @Override
    public Expression optimize(SessionLocal session) {
        boolean allConst = optimizeArguments(session, true);
        int len = args.length;
        if (len < 2 || len > 3) {
            throw DbException.get(ErrorCode.INVALID_PARAMETER_COUNT_2, getName(), "2..3");
        }
        TypeInfo argType = args[0].getType();
        long p = argType.getPrecision();
        if (args[1].isConstant()) {
            // if only two arguments are used,
            // subtract offset from first argument length
            p -= args[1].getValue(session).getLong() - 1;
        }
        if (args.length == 3 && args[2].isConstant()) {
            // if the third argument is constant it is at most this value
            p = Math.min(p, args[2].getValue(session).getLong());
        }
        p = Math.max(0, p);
        type = TypeInfo.getTypeInfo(
                DataType.isBinaryStringType(argType.getValueType()) ? Value.VARBINARY : Value.VARCHAR, p, 0, null);
        if (allConst) {
            return TypedValueExpression.getTypedIfNull(getValue(session), type);
        }
        return this;
    }

    @Override
    public StringBuilder getUnenclosedSQL(StringBuilder builder, int sqlFlags) {
        args[0].getUnenclosedSQL(builder.append(getName()).append('('), sqlFlags);
        args[1].getUnenclosedSQL(builder.append(" FROM "), sqlFlags);
        if (args.length > 2) {
            args[2].getUnenclosedSQL(builder.append(" FOR "), sqlFlags);
        }
        return builder.append(')');
    }

    @Override
    public String getName() {
        return "SUBSTRING";
    }

}
