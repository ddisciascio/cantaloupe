package edu.illinois.library.cantaloupe.processor;

import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.request.OutputFormat;
import edu.illinois.library.cantaloupe.request.Parameters;
import edu.illinois.library.cantaloupe.request.Quality;
import edu.illinois.library.cantaloupe.request.Region;
import edu.illinois.library.cantaloupe.request.Rotation;
import edu.illinois.library.cantaloupe.request.Size;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;

import javax.imageio.stream.ImageInputStream;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class CommonsImagingProcessor implements Processor {

    private static final Set<Quality> SUPPORTED_QUALITIES = new HashSet<>();
    private static final Set<ProcessorFeature> SUPPORTED_FEATURES =
            new HashSet<>();
    // Lazy-initialized by getFormats()
    private static Map<SourceFormat, Set<OutputFormat>> supportedFormats;

    static {
        SUPPORTED_QUALITIES.add(Quality.BITONAL);
        SUPPORTED_QUALITIES.add(Quality.COLOR);
        SUPPORTED_QUALITIES.add(Quality.DEFAULT);
        SUPPORTED_QUALITIES.add(Quality.GRAY);

        SUPPORTED_FEATURES.add(ProcessorFeature.MIRRORING);
        SUPPORTED_FEATURES.add(ProcessorFeature.REGION_BY_PERCENT);
        SUPPORTED_FEATURES.add(ProcessorFeature.REGION_BY_PIXELS);
        SUPPORTED_FEATURES.add(ProcessorFeature.ROTATION_ARBITRARY);
        SUPPORTED_FEATURES.add(ProcessorFeature.ROTATION_BY_90S);
        SUPPORTED_FEATURES.add(ProcessorFeature.SIZE_ABOVE_FULL);
        SUPPORTED_FEATURES.add(ProcessorFeature.SIZE_BY_FORCED_WIDTH_HEIGHT);
        SUPPORTED_FEATURES.add(ProcessorFeature.SIZE_BY_HEIGHT);
        SUPPORTED_FEATURES.add(ProcessorFeature.SIZE_BY_PERCENT);
        SUPPORTED_FEATURES.add(ProcessorFeature.SIZE_BY_WIDTH);
        SUPPORTED_FEATURES.add(ProcessorFeature.SIZE_BY_WIDTH_HEIGHT);
    }

    /**
     * @return Map of available output formats for all known source formats.
     */
    private static Map<SourceFormat, Set<OutputFormat>> getFormats() {
        if (supportedFormats == null) {
            supportedFormats = new HashMap<>();
            // this info is based on 1.0-SNAPSHOT as of 2015-01-12
            final Set<SourceFormat> sourceFormats = new HashSet<SourceFormat>();
            sourceFormats.add(SourceFormat.BMP);
            sourceFormats.add(SourceFormat.GIF);
            sourceFormats.add(SourceFormat.PNG);
            sourceFormats.add(SourceFormat.TIF);
            for (SourceFormat sourceFormat : sourceFormats) {
                Set<OutputFormat> outputFormats = new HashSet<>();
                outputFormats.add(OutputFormat.GIF);
                outputFormats.add(OutputFormat.PNG);
                outputFormats.add(OutputFormat.TIF);
                supportedFormats.put(sourceFormat, outputFormats);
            }
        }
        return supportedFormats;
    }

    public Set<OutputFormat> getAvailableOutputFormats(SourceFormat sourceFormat) {
        Set<OutputFormat> formats = getFormats().get(sourceFormat);
        if (formats == null) {
            formats = new HashSet<>();
        }
        return formats;
    }

    public Dimension getSize(ImageInputStream inputStream,
                             SourceFormat sourceFormat)
            throws ImageReadException, IOException {
        inputStream.mark();
        ImageInputStreamWrapper wrapper = new ImageInputStreamWrapper(inputStream);
        Dimension dim = Imaging.getImageSize(wrapper, "noop");
        inputStream.reset();
        return dim;
    }

    public Set<ProcessorFeature> getSupportedFeatures(SourceFormat sourceFormat) {
        return SUPPORTED_FEATURES;
    }

    public Set<Quality> getSupportedQualities(SourceFormat sourceFormat) {
        return SUPPORTED_QUALITIES;
    }

    public void process(Parameters params, SourceFormat sourceFormat,
                        ImageInputStream inputStream, OutputStream outputStream)
            throws Exception {
        final Set<OutputFormat> availableOutputFormats =
                getAvailableOutputFormats(sourceFormat);
        if (getAvailableOutputFormats(sourceFormat).size() < 1) {
            throw new UnsupportedSourceFormatException();
        } else if (!availableOutputFormats.contains(params.getOutputFormat())) {
            throw new UnsupportedOutputFormatException();
        }

        final ImageInputStreamWrapper wrapper = new ImageInputStreamWrapper(inputStream);
        final Map<String, Object> options = new HashMap<>();
        BufferedImage image = Imaging.getBufferedImage(wrapper, options);
        image = cropImage(image, params.getRegion());
        image = scaleImage(image, params.getSize());
        image = rotateImage(image, params.getRotation());
        image = filterImage(image, params.getQuality());
        outputImage(image, params.getOutputFormat(), outputStream);
    }

    private BufferedImage cropImage(BufferedImage inputImage, Region region) {
        BufferedImage croppedImage;
        if (region.isFull()) {
            croppedImage = inputImage;
        } else {
            int x, y, requestedWidth, requestedHeight, width, height;
            if (region.isPercent()) {
                x = (int) Math.round((region.getX() / 100.0) *
                        inputImage.getWidth());
                y = (int) Math.round((region.getY() / 100.0) *
                        inputImage.getHeight());
                requestedWidth = (int) Math.round((region.getWidth() / 100.0) *
                        inputImage.getWidth());
                requestedHeight = (int) Math.round((region.getHeight() / 100.0) *
                        inputImage.getHeight());
            } else {
                x = Math.round(region.getX());
                y = Math.round(region.getY());
                requestedWidth = Math.round(region.getWidth());
                requestedHeight = Math.round(region.getHeight());
            }
            // BufferedImage.getSubimage() will protest if asked for more
            // width/height than is available
            width = (x + requestedWidth > inputImage.getWidth()) ?
                    inputImage.getWidth() - x : requestedWidth;
            height = (y + requestedHeight > inputImage.getHeight()) ?
                    inputImage.getHeight() - y : requestedHeight;
            croppedImage = inputImage.getSubimage(x, y, width, height);
        }
        return croppedImage;
    }

    private BufferedImage filterImage(BufferedImage inputImage,
                                      Quality quality) {
        BufferedImage filteredImage = inputImage;
        if (quality != Quality.COLOR && quality != Quality.DEFAULT) {
            switch (quality) {
                case GRAY:
                    filteredImage = new BufferedImage(inputImage.getWidth(),
                            inputImage.getHeight(),
                            BufferedImage.TYPE_BYTE_GRAY);
                    break;
                case BITONAL:
                    filteredImage = new BufferedImage(inputImage.getWidth(),
                            inputImage.getHeight(),
                            BufferedImage.TYPE_BYTE_BINARY);
                    break;
            }
            Graphics2D g2d = filteredImage.createGraphics();
            g2d.drawImage(inputImage, 0, 0, null);
        }
        return filteredImage;
    }

    private BufferedImage rotateImage(BufferedImage inputImage,
                                      Rotation rotation) {
        // do mirroring
        BufferedImage mirroredImage = inputImage;
        if (rotation.shouldMirror()) {
            AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-mirroredImage.getWidth(null), 0);
            AffineTransformOp op = new AffineTransformOp(tx,
                    AffineTransformOp.TYPE_BILINEAR);
            mirroredImage = op.filter(mirroredImage, null);
        }
        // do rotation
        BufferedImage rotatedImage = mirroredImage;
        if (rotation.getDegrees() > 0) {
            double radians = Math.toRadians(rotation.getDegrees());
            int sourceWidth = mirroredImage.getWidth();
            int sourceHeight = mirroredImage.getHeight();
            int canvasWidth = (int) Math.round(Math.abs(sourceWidth *
                    Math.cos(radians)) + Math.abs(sourceHeight *
                    Math.sin(radians)));
            int canvasHeight = (int) Math.round(Math.abs(sourceHeight *
                    Math.cos(radians)) + Math.abs(sourceWidth *
                    Math.sin(radians)));

            // note: operations happen in reverse order of declaration
            AffineTransform tx = new AffineTransform();
            // 3. translate the image to the center of the "canvas"
            tx.translate(canvasWidth / 2, canvasHeight / 2);
            // 2. rotate it
            tx.rotate(radians);
            // 1. translate the image so that it is rotated about the center
            tx.translate(-sourceWidth / 2, -sourceHeight / 2);

            rotatedImage = new BufferedImage(canvasWidth, canvasHeight,
                    inputImage.getType());
            Graphics2D g2d = rotatedImage.createGraphics();
            RenderingHints hints = new RenderingHints(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHints(hints);
            g2d.drawImage(mirroredImage, tx, null);
        }
        return rotatedImage;
    }

    private BufferedImage scaleImage(BufferedImage inputImage, Size size) {
        // use G2D for no particular reason.
        return scaleImageWithG2d(inputImage, size);
    }

    /**
     * Scales an image using an AffineTransform.
     *
     * @param inputImage
     * @param size
     * @return
     */
    private BufferedImage scaleImageWithAffineTransform(
            BufferedImage inputImage, Size size) {
        BufferedImage scaledImage;
        if (size.getScaleMode() == Size.ScaleMode.FULL) {
            scaledImage = inputImage;
        } else {
            double xScale = 0.0f, yScale = 0.0f;
            if (size.getScaleMode() == Size.ScaleMode.ASPECT_FIT_WIDTH) {
                xScale = size.getWidth() / (double) inputImage.getWidth();
                yScale = xScale;
            } else if (size.getScaleMode() == Size.ScaleMode.ASPECT_FIT_HEIGHT) {
                yScale = size.getHeight() / (double) inputImage.getHeight();
                xScale = yScale;
            } else if (size.getScaleMode() == Size.ScaleMode.NON_ASPECT_FILL) {
                xScale = size.getWidth() / (double) inputImage.getWidth();
                yScale = size.getHeight() / (double) inputImage.getHeight();
            } else if (size.getScaleMode() == Size.ScaleMode.ASPECT_FIT_INSIDE) {
                double hScale = (double) size.getWidth() /
                        (double) inputImage.getWidth();
                double vScale = (double) size.getHeight() /
                        (double) inputImage.getHeight();
                xScale = inputImage.getWidth() * Math.min(hScale, vScale);
                yScale = inputImage.getHeight() * Math.min(hScale, vScale);
            } else if (size.getPercent() != null) {
                xScale = size.getPercent() / 100.0;
                yScale = xScale;
            }
            int width = (int) Math.round(inputImage.getWidth() * xScale);
            int height = (int) Math.round(inputImage.getHeight() * yScale);
            scaledImage = new BufferedImage(width, height, inputImage.getType());
            AffineTransform at = new AffineTransform();
            at.scale(xScale, yScale);
            AffineTransformOp scaleOp = new AffineTransformOp(at,
                    AffineTransformOp.TYPE_BILINEAR);
            scaledImage = scaleOp.filter(inputImage, scaledImage);
        }
        return scaledImage;
    }

    /**
     * Scales an image using Graphics2D.
     *
     * @param inputImage
     * @param size
     * @return
     */
    private BufferedImage scaleImageWithG2d(BufferedImage inputImage,
                                            Size size) {
        BufferedImage scaledImage;
        if (size.getScaleMode() == Size.ScaleMode.FULL) {
            scaledImage = inputImage;
        } else {
            int width = 0, height = 0;
            if (size.getScaleMode() == Size.ScaleMode.ASPECT_FIT_WIDTH) {
                width = size.getWidth();
                height = inputImage.getHeight() * width /
                        inputImage.getWidth();
            } else if (size.getScaleMode() == Size.ScaleMode.ASPECT_FIT_HEIGHT) {
                height = size.getHeight();
                width = inputImage.getWidth() * height /
                        inputImage.getHeight();
            } else if (size.getScaleMode() == Size.ScaleMode.NON_ASPECT_FILL) {
                width = size.getWidth();
                height = size.getHeight();
            } else if (size.getScaleMode() == Size.ScaleMode.ASPECT_FIT_INSIDE) {
                double hScale = (double) size.getWidth() /
                        (double) inputImage.getWidth();
                double vScale = (double) size.getHeight() /
                        (double) inputImage.getHeight();
                width = (int) Math.round(inputImage.getWidth() *
                        Math.min(hScale, vScale));
                height = (int) Math.round(inputImage.getHeight() *
                        Math.min(hScale, vScale));
            } else if (size.getPercent() != null) {
                width = (int) Math.round(inputImage.getWidth() *
                        (size.getPercent() / 100.0));
                height = (int) Math.round(inputImage.getHeight() *
                        (size.getPercent() / 100.0));
            }
            scaledImage = new BufferedImage(width, height,
                    inputImage.getType());
            Graphics2D g2d = scaledImage.createGraphics();
            RenderingHints hints = new RenderingHints(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHints(hints);
            g2d.drawImage(inputImage, 0, 0, width, height, null);
            g2d.dispose();
        }
        return scaledImage;
    }

    private void outputImage(BufferedImage image, OutputFormat outputFormat,
                             OutputStream outputStream)
            throws ImageWriteException, IOException {
        ImageFormat format;
        switch (outputFormat) {
            case GIF:
                format = ImageFormats.valueOf("GIF");
                break;
            case PNG:
                format = ImageFormats.valueOf("PNG");
                break;
            case TIF:
                format = ImageFormats.valueOf("TIFF");
                break;
            default:
                throw new UnsupportedOutputFormatException();
        }
        Imaging.writeImage(image, outputStream, format, null);
    }

}
