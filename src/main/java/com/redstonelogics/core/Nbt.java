package com.redstonelogics.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.ibm.icu.impl.locale.LikelySubtags.Data;

public final class Nbt {
    public static final int END = 0;
    public static final int BYTE = 1;
    public static final int SHORT = 2;
    public static final int INT = 3;
    public static final int LONG = 4;
    public static final int FLOAT = 5;
    public static final int DOUBLE = 6;
    public static final int BYTE_ARRAY = 7;
    public static final int STRING = 8;
    public static final int LIST = 9;
    public static final int COMPOUND = 10;
    public static final int INT_ARRAY = 11;
    public static final int LONG_ARRAY = 12;

    public static final class Tag {
        public final Map<String, Object> values = new LinkedHashMap<>();

        public Tag compound(String name){
            Object v = values.get(name);
            return v instanceof Tag t ? t : null;
        }

        public int integer(String name, int fallback) {
            Object v = values.get(name);
            return v instanceof Number n ? n.intValue() : fallback;
        }

        public String string(String name) {
            Object v = values.get(name);
            return v instanceof String s ? s : null;
        }

        public byte[] bytes(String name) {
            Object v = values.get(name);
            return v instanceof byte[] b ? b : null;
        }

        @SuppressWarnings("unchecked")
        public List<Object> list(String name) {
            Object v = values.get(name);
            return v instanceof List<?> l ? (List<Object>) l : List.of();
        }

        public boolean has(String name) {
            return values.containsKey(name);
        }
    }

    private Nbt() {
    }

    public static Tag read(byte[] raw) throws IOException {
        InputStream in = new ByteArrayInputStream(raw);
        if (raw.length > 1 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            in = new GZIPInputStream(in);
        }
        try (DataInputStream data = new DataInputStream(in)) {
            int type = data.readUnsignedByte();
            if (type != COMPOUND) {
                throw new IOException("Root tag is " + type + ", expected a compound.");
            }
            data.readUTF();                       // root name, conventionally empty
            return readCompound(data);
        }
    }

    private static Tag readCompound(DataInputStream in) throws IOException {
        Tag tag = new Tag();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == END) {
                return tag;
            }
            String name = in.readUTF();
            tag.values.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case BYTE:
                return in.readByte();
            case SHORT:
                return in.readShort();
            case INT:
                return in.readInt();
            case LONG:
                return in.readLong();
            case FLOAT:
                return in.readFloat();
            case DOUBLE:
                return in.readDouble();
            case BYTE_ARRAY: {
                byte[] out = new byte[in.readInt()];
                in.readFully(out);
                return out;
            }
            case STRING:
                return in.readUTF();
            case LIST: {
                int itemType = in.readUnsignedByte();
                int count = in.readInt();
                List<Object> out = new ArrayList<>(Math.max(0, count));
                for (int i = 0; i < count; i++) {
                    out.add(readPayload(in, itemType));
                }
                return out;
            }
            case COMPOUND:
                return readCompound(in);
            case INT_ARRAY: {
                int[] out = new int[in.readInt()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = in.readInt();
                }
                return out;
            }
            case LONG_ARRAY: {
                long[] out = new long[in.readInt()];
                for (int i = 0; i < out.length; i++) {
                    out[i] = in.readLong();
                }
                return out;
            }
            default:
                throw new IOException("Unknown NBT tag type " + type);
        }
    }
}