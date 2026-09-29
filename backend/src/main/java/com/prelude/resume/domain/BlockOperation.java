package com.prelude.resume.domain;

/**
 * One block-level change a proposal wants to make.
 *
 * <p>Patching is expressed as operations on addressed blocks rather than as a rewritten
 * document so a reviewer can see which parts of the resume a model touched, and so an
 * accepted patch cannot quietly carry changes to blocks nobody asked about.
 */
public record BlockOperation(Kind kind, String blockId, String section, String text) {

    public enum Kind {
        REPLACE,
        INSERT,
        DELETE
    }

    public static BlockOperation replace(String blockId, String text) {
        return new BlockOperation(Kind.REPLACE, blockId, null, text);
    }

    public static BlockOperation insert(String section, String text) {
        return new BlockOperation(Kind.INSERT, null, section, text);
    }

    public static BlockOperation delete(String blockId) {
        return new BlockOperation(Kind.DELETE, blockId, null, null);
    }

    /**
     * A delete names a block and changes no text; an insert creates one. Everything else
     * has to carry the text it puts there.
     */
    public boolean needsText() {
        return kind != Kind.DELETE;
    }
}
