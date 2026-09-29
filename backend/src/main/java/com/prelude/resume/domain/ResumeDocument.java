package com.prelude.resume.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The resume document as a flat, ordered block tree — the single source of truth every
 * revision stores and every patch proposal edits.
 *
 * <p>Block ids are assigned once, when a document is first built from an import, and are
 * carried forward by every later revision. That stability is what lets a proposal name the
 * blocks it touches and let the workspace highlight them, and what makes a stale proposal
 * detectable: the block it meant to change is either still there or it is not.
 */
public record ResumeDocument(List<Block> blocks) {

    public ResumeDocument {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }

    /**
     * A block is addressed by id and read as one of three kinds: a section heading, an
     * item under a section, or a bullet inside an item.
     */
    public record Block(String id, String section, String kind, String text) {

        public static final String SECTION = "section";
        public static final String ITEM = "item";
        public static final String BULLET = "bullet";
    }

    public Optional<Block> block(String id) {
        return blocks.stream().filter(candidate -> candidate.id().equals(id)).findFirst();
    }

    public List<String> blockIds() {
        return blocks.stream().map(Block::id).toList();
    }

    /**
     * Apply one proposal's operations and report what the document became.
     *
     * <p>Operations are applied in order against the same working list, so an insert can
     * be addressed by a later replace. The result keeps every untouched block byte-for-byte
     * — the diff a reviewer sees is exactly the set of blocks that changed.
     */
    public Applied apply(List<BlockOperation> operations) {
        List<Block> working = new ArrayList<>(blocks);
        List<String> affected = new ArrayList<>();
        int added = 0;
        int removed = 0;

        for (BlockOperation operation : operations) {
            int index = indexOf(working, operation.blockId());
            if (operation.kind() != BlockOperation.Kind.INSERT && index < 0) {
                throw new IllegalArgumentException(
                    "补丁引用了文档中不存在的块: " + operation.blockId());
            }
            switch (operation.kind()) {
                case REPLACE -> {
                    Block before = working.get(index);
                    working.set(index,
                        new Block(before.id(), before.section(), before.kind(), operation.text()));
                    affected.add(before.id());
                    TextDiff.Count lines = TextDiff.count(before.text(), operation.text());
                    added += lines.added();
                    removed += lines.removed();
                }
                case DELETE -> {
                    Block before = working.remove(index);
                    affected.add(before.id());
                    removed += TextDiff.count(before.text(), "").removed();
                }
                case INSERT -> {
                    String newId = nextId(working, operation.section());
                    working.add(index < 0 ? working.size() : index + 1,
                        new Block(newId, operation.section(), Block.BULLET, operation.text()));
                    affected.add(newId);
                    added += TextDiff.count("", operation.text()).added();
                }
            }
        }
        return new Applied(new ResumeDocument(working), affected, added, removed);
    }

    private static int indexOf(List<Block> working, String blockId) {
        for (int index = 0; index < working.size(); index++) {
            if (working.get(index).id().equals(blockId)) {
                return index;
            }
        }
        return -1;
    }

    private static String nextId(List<Block> working, String section) {
        List<String> used = working.stream().map(Block::id).toList();
        int suffix = working.size();
        String candidate = section + "-" + suffix;
        while (used.contains(candidate)) {
            candidate = section + "-" + (++suffix);
        }
        return candidate;
    }

    /** The document after a patch, plus the facts a reviewer needs to judge it. */
    public record Applied(
        ResumeDocument document,
        List<String> affectedBlockIds,
        int linesAdded,
        int linesRemoved
    ) {
        public Applied {
            affectedBlockIds = affectedBlockIds == null ? List.of() : List.copyOf(affectedBlockIds);
        }
    }
}
