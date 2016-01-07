package edu.illinois.library.cantaloupe.processor;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.image.Crop;
import edu.illinois.library.cantaloupe.image.Filter;
import edu.illinois.library.cantaloupe.image.Operation;
import edu.illinois.library.cantaloupe.image.OperationList;
import edu.illinois.library.cantaloupe.image.Rotate;
import edu.illinois.library.cantaloupe.image.Scale;
import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.image.OutputFormat;
import edu.illinois.library.cantaloupe.image.Transpose;
import edu.illinois.library.cantaloupe.resolver.ChannelSource;
import edu.illinois.library.cantaloupe.resource.iiif.ProcessorFeature;
import org.apache.commons.configuration.Configuration;
import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.core.Info;
import org.im4java.process.Pipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>Processor using the GraphicsMagick <code>gm</code> command-line tool.</p>
 *
 * <p>Does not implement <code>FileProcessor</code> because testing indicates
 * that input streams are significantly faster.</p>
 */
class GraphicsMagickProcessor implements ChannelProcessor {

    private static Logger logger = LoggerFactory.
            getLogger(GraphicsMagickProcessor.class);

    private static final String BINARIES_PATH_CONFIG_KEY =
            "GraphicsMagickProcessor.path_to_binaries";
    private static final Set<ProcessorFeature> SUPPORTED_FEATURES =
            new HashSet<>();
    private static final Set<edu.illinois.library.cantaloupe.resource.iiif.v1.Quality>
            SUPPORTED_IIIF_1_1_QUALITIES = new HashSet<>();
    private static final Set<edu.illinois.library.cantaloupe.resource.iiif.v2.Quality>
            SUPPORTED_IIIF_2_0_QUALITIES = new HashSet<>();
    // Lazy-initialized by getFormats()
    private static HashMap<SourceFormat, Set<OutputFormat>> supportedFormats;

    static {
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.BITONAL);
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.COLOR);
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.GRAY);
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.NATIVE);

        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.BITONAL);
        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.COLOR);
        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.DEFAULT);
        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.GRAY);

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
     * @return Map of available output formats for all known source formats,
     * based on information reported by <code>gm version</code>.
     */
    private static HashMap<SourceFormat, Set<OutputFormat>> getFormats() {
        if (supportedFormats == null) {
            final Set<SourceFormat> sourceFormats = new HashSet<>();
            final Set<OutputFormat> outputFormats = new HashSet<>();
            String cmdPath = "gm";
            try {
                // retrieve the output of the `gm version` command, which
                // contains a list of all optional formats
                Configuration config = Application.getConfiguration();
                String pathPrefix = config.getString(BINARIES_PATH_CONFIG_KEY);
                if (pathPrefix != null) {
                    cmdPath = pathPrefix + File.separator + cmdPath;
                }
                String[] commands = {cmdPath, "version"};
                Runtime runtime = Runtime.getRuntime();
                Process proc = runtime.exec(commands);
                BufferedReader stdInput = new BufferedReader(
                        new InputStreamReader(proc.getInputStream()));
                String s;
                boolean read = false;
                while ((s = stdInput.readLine()) != null) {
                    s = s.trim();
                    if (s.contains("Feature Support")) {
                        read = true; // start reading
                    } else if (s.contains("Host type:")) {
                        break; // stop reading
                    }
                    if (read) {
                        if (s.startsWith("JPEG-2000 ") && s.endsWith(" yes")) {
                            sourceFormats.add(SourceFormat.JP2);
                            outputFormats.add(OutputFormat.JP2);
                        } else if (s.startsWith("JPEG ") && s.endsWith(" yes")) {
                            sourceFormats.add(SourceFormat.JPG);
                            outputFormats.add(OutputFormat.JPG);
                        } else if (s.startsWith("PNG ") && s.endsWith(" yes")) {
                            sourceFormats.add(SourceFormat.PNG);
                            outputFormats.add(OutputFormat.PNG);
                        } else if (s.startsWith("Ghostscript") && s.endsWith(" yes")) {
                            outputFormats.add(OutputFormat.PDF);
                        } else if (s.startsWith("TIFF ") && s.endsWith(" yes")) {
                            sourceFormats.add(SourceFormat.TIF);
                            outputFormats.add(OutputFormat.TIF);
                        } else if (s.startsWith("WebP ") && s.endsWith(" yes")) {
                            sourceFormats.add(SourceFormat.WEBP);
                            outputFormats.add(OutputFormat.WEBP);
                        }
                    }
                }

                // add formats that are not listed in the output of "gm version"
                // but are definitely available
                // (http://www.graphicsmagick.org/formats.html)
                sourceFormats.add(SourceFormat.BMP);
                sourceFormats.add(SourceFormat.GIF);
                // GIF output is buggy
                //outputFormats.add(OutputFormat.GIF);
            } catch (IOException e) {
                logger.error("Failed to execute {}", cmdPath);
            }

            supportedFormats = new HashMap<>();
            for (SourceFormat sourceFormat : sourceFormats) {
                supportedFormats.put(sourceFormat, outputFormats);
            }
        }
        return supportedFormats;
    }

    @Override
    public Set<OutputFormat> getAvailableOutputFormats(SourceFormat sourceFormat) {
        Set<OutputFormat> formats = getFormats().get(sourceFormat);
        if (formats == null) {
            formats = new HashSet<>();
        }
        return formats;
    }

    @Override
    public Dimension getSize(final ReadableByteChannel readableChannel,
                             final SourceFormat sourceFormat)
            throws ProcessorException {
        if (getAvailableOutputFormats(sourceFormat).size() < 1) {
            throw new UnsupportedSourceFormatException(sourceFormat);
        }
        try {
            Info sourceInfo = new Info(
                    sourceFormat.getPreferredExtension() + ":-",
                    Channels.newInputStream(readableChannel), true);
            return new Dimension(sourceInfo.getImageWidth(),
                    sourceInfo.getImageHeight());
        } catch (IM4JavaException e) {
            throw new ProcessorException(e.getMessage(), e);
        } finally {
            try {
                readableChannel.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public Set<ProcessorFeature> getSupportedFeatures(
            final SourceFormat sourceFormat) {
        Set<ProcessorFeature> features = new HashSet<>();
        if (getAvailableOutputFormats(sourceFormat).size() > 0) {
            features.addAll(SUPPORTED_FEATURES);
        }
        return features;
    }

    @Override
    public Set<edu.illinois.library.cantaloupe.resource.iiif.v1.Quality>
    getSupportedIiif1_1Qualities(final SourceFormat sourceFormat) {
        Set<edu.illinois.library.cantaloupe.resource.iiif.v1.Quality>
                qualities = new HashSet<>();
        if (getAvailableOutputFormats(sourceFormat).size() > 0) {
            qualities.addAll(SUPPORTED_IIIF_1_1_QUALITIES);
        }
        return qualities;
    }

    @Override
    public Set<edu.illinois.library.cantaloupe.resource.iiif.v2.Quality>
    getSupportedIiif2_0Qualities(final SourceFormat sourceFormat) {
        Set<edu.illinois.library.cantaloupe.resource.iiif.v2.Quality>
                qualities = new HashSet<>();
        if (getAvailableOutputFormats(sourceFormat).size() > 0) {
            qualities.addAll(SUPPORTED_IIIF_2_0_QUALITIES);
        }
        return qualities;
    }

    @Override
    public void process(final OperationList ops,
                        final SourceFormat sourceFormat,
                        final Dimension fullSize,
                        final ChannelSource channelSource,
                        final WritableByteChannel writableChannel)
            throws ProcessorException {
        doProcess(sourceFormat.getPreferredExtension() + ":-", channelSource,
                ops, sourceFormat, fullSize, writableChannel);
    }

    private void assembleOperation(IMOperation imOp, OperationList ops,
                                   Dimension fullSize) {
        for (Operation op : ops) {
            if (op instanceof Crop) {
                Crop crop = (Crop) op;
                if (!crop.isNoOp()) {
                    if (crop.getUnit().equals(Crop.Unit.PERCENT)) {
                        // im4java doesn't support cropping x/y by percentage
                        // (only width/height), so we have to calculate them.
                        int x = Math.round(crop.getX() * fullSize.width);
                        int y = Math.round(crop.getY() * fullSize.height);
                        int width = Math.round(crop.getWidth() * 100);
                        int height = Math.round(crop.getHeight() * 100);
                        imOp.crop(width, height, x, y, "%");
                    } else {
                        imOp.crop(Math.round(crop.getWidth()),
                                Math.round(crop.getHeight()),
                                Math.round(crop.getX()),
                                Math.round(crop.getY()));
                    }
                }
            } else if (op instanceof Scale) {
                Scale scale = (Scale) op;
                if (!scale.isNoOp()) {
                    if (scale.getMode() == Scale.Mode.ASPECT_FIT_WIDTH) {
                        imOp.resize(scale.getWidth());
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_HEIGHT) {
                        imOp.resize(null, scale.getHeight());
                    } else if (scale.getMode() == Scale.Mode.NON_ASPECT_FILL) {
                        imOp.resize(scale.getWidth(), scale.getHeight(), "!");
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_INSIDE) {
                        imOp.resize(scale.getWidth(), scale.getHeight());
                    } else if (scale.getPercent() != null) {
                        imOp.resize(Math.round(scale.getPercent() * 100),
                                Math.round(scale.getPercent() * 100), "%");
                    }
                }
            } else if (op instanceof Transpose) {
                switch ((Transpose) op) {
                    case HORIZONTAL:
                        imOp.flop();
                        break;
                    case VERTICAL:
                        imOp.flip();
                        break;
                }
            } else if (op instanceof Rotate) {
                Rotate rotate = (Rotate) op;
                if (!rotate.isNoOp()) {
                    imOp.rotate((double) rotate.getDegrees());
                }
            } else if (op instanceof Filter) {
                switch ((Filter) op) {
                    case GRAY:
                        imOp.colorspace("Gray");
                        break;
                    case BITONAL:
                        imOp.monochrome();
                        break;
                }
            }
        }
    }

    /**
     * @param inputPath Absolute filename pathname or "-" to use a stream
     * @param channelSource
     * @param ops
     * @param sourceFormat
     * @param fullSize
     * @param writableChannel Stream to write to
     * @throws ProcessorException
     */
    private void doProcess(final String inputPath,
                           final ChannelSource channelSource,
                           final OperationList ops,
                           final SourceFormat sourceFormat,
                           final Dimension fullSize,
                           final WritableByteChannel writableChannel)
            throws ProcessorException {
        final Set<OutputFormat> availableOutputFormats =
                getAvailableOutputFormats(sourceFormat);
        if (getAvailableOutputFormats(sourceFormat).size() < 1) {
            throw new UnsupportedSourceFormatException(sourceFormat);
        } else if (!availableOutputFormats.contains(ops.getOutputFormat())) {
            throw new UnsupportedOutputFormatException();
        }

        try {
            IMOperation op = new IMOperation();
            op.addImage(inputPath);
            assembleOperation(op, ops, fullSize);

            op.addImage(ops.getOutputFormat().getExtension() + ":-"); // write to stdout

            ConvertCmd convert = new ConvertCmd(true);

            String binaryPath = Application.getConfiguration().
                    getString(BINARIES_PATH_CONFIG_KEY, "");
            if (binaryPath.length() > 0) {
                convert.setSearchPath(binaryPath);
            }

            convert.setInputProvider(new Pipe(
                    Channels.newInputStream(channelSource.newChannel()), null));
            convert.setOutputConsumer(
                    new Pipe(null, Channels.newOutputStream(writableChannel)));
            convert.run(op);
        } catch (InterruptedException | IM4JavaException | IOException e) {
            throw new ProcessorException(e.getMessage(), e);
        }
    }

}
