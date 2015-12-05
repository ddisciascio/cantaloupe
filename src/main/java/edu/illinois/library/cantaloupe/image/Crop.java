package edu.illinois.library.cantaloupe.image;

import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * <p>Encapsulates a cropping operation.</p>
 *
 * <p>Note that {@link #isFull()} can be assumed to take precedence over all
 * other properties.</p>
 */
public class Crop implements Operation {

    public enum Unit {
        PERCENT, PIXELS;
    }

    private float height = 0.0f;
    private boolean isFull = false;
    private Unit unit = Unit.PIXELS;
    private float width = 0.0f;
    private float x = 0.0f;
    private float y = 0.0f;

    /**
     * @return The height of the operation. If {@link #getUnit()} returns
     * {@link Unit#PERCENT}, this will be a percentage of the full image height
     * between 0 and 1.
     */
    public float getHeight() {
        return height;
    }

    /**
     * @param fullSize Full-sized image dimensions.
     * @return Crop coordinates relative to the given full-sized image
     * dimensions.
     */
    public Rectangle getRectangle(Dimension fullSize) {
        int x, y, width, height;
        if (this.isFull()) {
            x = 0;
            y = 0;
            width = fullSize.width;
            height = fullSize.height;
        } else if (this.getUnit().equals(Unit.PERCENT)) {
            x = Math.round(this.getX() * fullSize.width);
            y = Math.round(this.getY() * fullSize.height);
            width = Math.round(this.getWidth() * fullSize.width);
            height = Math.round(this.getHeight() * fullSize.height);
        } else {
            x = Math.round(this.getX());
            y = Math.round(this.getY());
            width = Math.round(this.getWidth());
            height = Math.round(this.getHeight());
        }
        return new Rectangle(x, y, width, height);
    }

    @Override
    public Dimension getResultingSize(Dimension fullSize) {
        return getRectangle(fullSize).getSize();
    }

    public Unit getUnit() {
        return unit;
    }

    /**
     * @return The width of the operation. If {@link #getUnit()} returns
     * {@link Unit#PERCENT}, this will be a percentage of the full image width
     * between 0 and 1.
     */
    public float getWidth() {
        return width;
    }

    /**
     * @return The left bounding coordinate of the operation. If
     * {@link #getUnit()} returns {@link Unit#PERCENT}, this will be a
     * percentage of the full image width between 0 and 1.
     */
    public float getX() {
        return x;
    }

    /**
     * @return The top bounding coordinate of the operation. If
     * {@link #getUnit()} returns {@link Unit#PERCENT}, this will be a
     * percentage of the full image height between 0 and 1.
     */
    public float getY() {
        return y;
    }

    /**
     * @return Whether the crop specifies the full source area, i.e. whether it
     * is effectively a no-op.
     */
    public boolean isFull() {
        return this.isFull;
    }

    /**
     * @return Whether the crop is effectively a no-op.
     */
    public boolean isNoOp() {
        if (this.isFull()) {
            return true;
        }
        if (this.getUnit().equals(Unit.PERCENT) &&
                Math.abs(this.getWidth() - 1f) < 0.000001f &&
                Math.abs(this.getHeight() - 1f) < 0.000001f) {
            return true;
        }
        return false;
    }

    public void setFull(boolean isFull) {
        this.isFull = isFull;
    }

    public void setHeight(float height) throws IllegalArgumentException {
        if (height <= 0) {
            throw new IllegalArgumentException("Height must be a positive integer");
        } else if (this.getUnit().equals(Unit.PERCENT) && height > 1) {
            throw new IllegalArgumentException("Height percentage must be <= 1");
        }
        this.height = height;
    }

    public void setUnit(Unit unit) {
        this.unit = unit;
    }

    public void setWidth(float width) throws IllegalArgumentException {
        if (width <= 0) {
            throw new IllegalArgumentException("Width must be a positive integer");
        } else if (this.getUnit().equals(Unit.PERCENT) && width > 1) {
            throw new IllegalArgumentException("Width percentage must be <= 1");
        }
        this.width = width;
    }

    public void setX(float x) throws IllegalArgumentException {
        if (x < 0) {
            throw new IllegalArgumentException("X must be a positive float");
        } else if (this.getUnit().equals(Unit.PERCENT) && x > 1) {
            throw new IllegalArgumentException("X percentage must be <= 1");
        }
        this.x = x;
    }

    public void setY(float y) throws IllegalArgumentException {
        if (y < 0) {
            throw new IllegalArgumentException("Y must be a positive float");
        } else if (this.getUnit().equals(Unit.PERCENT) && y > 1) {
            throw new IllegalArgumentException("Y percentage must be <= 1");
        }
        this.y = y;
    }

    /**
     * @return String representation of the instance, guaranteed to represent
     * the instance, but not guaranteed to have any particular format.
     */
    @Override
    public String toString() {
        String str = "";
        if (this.isNoOp()) {
            str += "full";
        } else {
            String x, y;
            if (this.getUnit().equals(Unit.PERCENT)) {
                x = NumberUtil.removeTrailingZeroes(this.getX());
                y = NumberUtil.removeTrailingZeroes(this.getY());
                str += "pct:";
            } else {
                x = Integer.toString(Math.round(this.getX()));
                y = Integer.toString(Math.round(this.getY()));
            }
            str += String.format("%s,%s,%s,%s", x, y,
                    NumberUtil.removeTrailingZeroes(this.getWidth()),
                    NumberUtil.removeTrailingZeroes(this.getHeight()));
        }
        return str;
    }

}
