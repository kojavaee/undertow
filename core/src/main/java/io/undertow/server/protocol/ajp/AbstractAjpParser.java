package io.undertow.server.protocol.ajp;

import io.undertow.util.HttpString;

import java.nio.ByteBuffer;

/**
 * @author Stuart Douglas
 */
public abstract class AbstractAjpParser {

    public static final int STRING_LENGTH_MASK = 1 << 31;

    protected IntegerHolder parse16BitInteger(ByteBuffer buf, AbstractAjpParseState state) {
        if (!buf.hasRemaining()) {
            return new IntegerHolder(-1, false);
        }
        int number = state.currentIntegerPart;
        if (number == -1) {
            number = (buf.get() & 0xFF);
        }
        if (buf.hasRemaining()) {
            final byte b = buf.get();
            int result = ((0xFF & number) << 8) + (b & 0xFF);
            state.currentIntegerPart = -1;
            return new IntegerHolder(result, true);
        } else {
            state.currentIntegerPart = number;
            return new IntegerHolder(-1, false);
        }
    }

    protected StringHolder parseString(ByteBuffer buf, AbstractAjpParseState state, boolean header) {
        if (!buf.hasRemaining()) {
            return new StringHolder(null, false);
        }
        int stringLength = state.stringLength;
        if (stringLength == -1) {
            int number = buf.get() & 0xFF;
            if (buf.hasRemaining()) {
                final byte b = buf.get();
                stringLength = ((0xFF & number) << 8) + (b & 0xFF);
            } else {
                state.stringLength = number | STRING_LENGTH_MASK;
                return new StringHolder(null, false);
            }
        } else if ((stringLength & STRING_LENGTH_MASK) != 0) {
            int number = stringLength & ~STRING_LENGTH_MASK;
            stringLength = ((0xFF & number) << 8) + (buf.get() & 0xFF);
        }
        if (header && (stringLength & 0xFF00) != 0) {
            state.stringLength = -1;
            return new StringHolder(headers(stringLength & 0xFF));
        }
        if (stringLength == 0xFFFF) {
            //OxFFFF means null
            state.stringLength = -1;
            return new StringHolder(null, true);
        }
        StringBuilder builder = state.currentString;

        if (builder == null) {
            builder = new StringBuilder();
            state.currentString = builder;
        }
        int length = builder.length();
        while (length < stringLength) {
            if (!buf.hasRemaining()) {
                state.stringLength = stringLength;
                return new StringHolder(null, false);
            }
            builder.append((char) buf.get());
            ++length;
        }

        if (buf.hasRemaining()) {
            buf.get(); //null terminator
            state.currentString = null;
            state.stringLength = -1;
            return new StringHolder(builder.toString(), true);
        } else {
            return new StringHolder(null, false);
        }
    }

    protected abstract HttpString headers(int offset);

    protected static class IntegerHolder {
        public final int value;
        public final boolean readComplete;

        private IntegerHolder(int value, boolean readComplete) {
            this.value = value;
            this.readComplete = readComplete;
        }
    }

    protected static class StringHolder {
        public final String value;
        public final HttpString header;
        public final boolean readComplete;

        private StringHolder(String value, boolean readComplete) {
            this.value = value;
            this.readComplete = readComplete;
            this.header = null;
        }

        private StringHolder(HttpString value) {
            this.value = null;
            this.readComplete = true;
            this.header = value;
        }
    }
}
