package com.interview.identity.infrastructure.avatar;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Validates the WebP container only. It intentionally does not claim to decode WebP.
 */
public final class LegacyWebpValidator {

    private static final int RIFF_HEADER_LENGTH = 12;
    private static final Set<String> KNOWN_CHUNKS = Set.of(
        "VP8 ", "VP8L", "VP8X", "ALPH", "ANIM", "ANMF", "ICCP", "EXIF", "XMP "
    );

    private LegacyWebpValidator() {
    }

    public static boolean isValid(byte[] bytes) {
        if (bytes == null || bytes.length < 20
            || !ascii(bytes, 0, "RIFF")
            || !ascii(bytes, 8, "WEBP")) {
            return false;
        }
        long riffSize = littleEndianUInt32(bytes, 4);
        if (riffSize != bytes.length - 8) {
            return false;
        }

        int offset = RIFF_HEADER_LENGTH;
        boolean hasImageChunk = false;
        while (offset < bytes.length) {
            if (bytes.length - offset < 8) {
                return false;
            }
            String chunkType = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long chunkSize = littleEndianUInt32(bytes, offset + 4);
            if (!KNOWN_CHUNKS.contains(chunkType)
                || !isValidChunkSize(chunkType, chunkSize)) {
                return false;
            }
            long chunkEnd = offset + 8L + chunkSize + (chunkSize & 1L);
            if (chunkEnd > bytes.length || chunkEnd < offset + 8L) {
                return false;
            }
            if (!isValidImageChunk(bytes, offset, chunkType, chunkSize)) {
                return false;
            }
            if (chunkType.equals("VP8 ") || chunkType.equals("VP8L") || chunkType.equals("ANMF")) {
                hasImageChunk = true;
            }
            offset = (int) chunkEnd;
        }
        return offset == bytes.length && hasImageChunk;
    }

    private static boolean isValidImageChunk(byte[] bytes, int offset, String chunkType, long chunkSize) {
        int dataOffset = offset + 8;
        if (chunkType.equals("VP8 ")) {
            return chunkSize >= 10
                && bytes[dataOffset + 3] == (byte) 0x9d
                && bytes[dataOffset + 4] == 0x01
                && bytes[dataOffset + 5] == 0x2a
                && littleEndianUInt16(bytes, dataOffset + 6) > 0
                && littleEndianUInt16(bytes, dataOffset + 8) > 0;
        }
        if (chunkType.equals("VP8L")) {
            return chunkSize >= 5
                && (bytes[dataOffset] & 0xff) == 0x2f;
        }
        if (chunkType.equals("VP8X")) {
            return chunkSize == 10
                && littleEndianUInt24(bytes, dataOffset + 4) > 0
                && littleEndianUInt24(bytes, dataOffset + 7) > 0;
        }
        return true;
    }

    private static boolean isValidChunkSize(String chunkType, long chunkSize) {
        return switch (chunkType) {
            case "VP8 " -> chunkSize >= 10;
            case "VP8L" -> chunkSize >= 5;
            case "VP8X" -> chunkSize == 10;
            case "ANIM" -> chunkSize == 6;
            case "ANMF" -> chunkSize >= 16;
            case "ALPH" -> chunkSize >= 1;
            default -> true;
        };
    }

    private static boolean ascii(byte[] bytes, int offset, String value) {
        return offset >= 0 && offset + value.length() <= bytes.length
            && new String(bytes, offset, value.length(), StandardCharsets.US_ASCII).equals(value);
    }

    private static long littleEndianUInt32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
            | ((bytes[offset + 1] & 0xffL) << 8)
            | ((bytes[offset + 2] & 0xffL) << 16)
            | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static int littleEndianUInt16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int littleEndianUInt24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
            | ((bytes[offset + 1] & 0xff) << 8)
            | ((bytes[offset + 2] & 0xff) << 16);
    }
}
