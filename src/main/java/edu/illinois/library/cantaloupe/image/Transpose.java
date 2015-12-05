package edu.illinois.library.cantaloupe.image;

import java.awt.Dimension;

/**
 * Encapsulates a transposition (flipping/mirroring) operation on an image.
 */
public enum Transpose implements Operation {

    /** Indicates mirroring. */
    HORIZONTAL,
    /** Indicates flipping. */
    VERTICAL;

    @Override
    public Dimension getResultingSize(Dimension fullSize) {
        return fullSize;
    }

    public boolean isNoOp() {
        return false;
    }

    /**
     * @return String representation of the instance, guaranteed to represent
     * the instance, but not guaranteed to have any particular format.
     */
    @Override
    public String toString() {
        switch (this) {
            case HORIZONTAL:
                return "h";
            case VERTICAL:
                return "v";
            default:
                return super.toString();
        }
    }
}
