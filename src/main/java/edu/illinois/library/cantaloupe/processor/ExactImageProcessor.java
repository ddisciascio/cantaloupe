package edu.illinois.library.cantaloupe.processor;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.image.Crop;
import edu.illinois.library.cantaloupe.image.Filter;
import edu.illinois.library.cantaloupe.image.Operation;
import edu.illinois.library.cantaloupe.image.OperationList;
import edu.illinois.library.cantaloupe.image.OutputFormat;
import edu.illinois.library.cantaloupe.image.Rotate;
import edu.illinois.library.cantaloupe.image.Scale;
import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.image.Transpose;
import edu.illinois.library.cantaloupe.resource.iiif.ProcessorFeature;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Processor using the econvert and eidentify tools from the
 * <a href="http://www.exactcode.com/opensource/exactimage/">ExactImage
 * toolkit</a>.
 */
class ExactImageProcessor implements FileProcessor {

    private class StreamCopier implements Runnable {

        private final InputStream inputStream;
        private final OutputStream outputStream;

        public StreamCopier(InputStream is, OutputStream os) {
            inputStream = is;
            outputStream = os;
        }

        public void run() {
            try {
                IOUtils.copy(inputStream, outputStream);
            } catch (IOException e) {
                if (!e.getMessage().startsWith("Broken pipe")) {
                    logger.error(e.getMessage(), e);
                }
            }
        }

    }

    private static Logger logger = LoggerFactory.
            getLogger(ExactImageProcessor.class);

    private static final String BINARIES_PATH_CONFIG_KEY =
            "ExactImageProcessor.path_to_binaries";
    private static final Set<ProcessorFeature> SUPPORTED_FEATURES =
            new HashSet<>();
    private static final Set<edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality>
            SUPPORTED_IIIF_1_1_QUALITIES = new HashSet<>();
    private static final Set<edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality>
            SUPPORTED_IIIF_2_0_QUALITIES = new HashSet<>();

    // Lazy-initialized by getFormats()
    private static HashMap<SourceFormat, Set<OutputFormat>> supportedFormats;

    static {
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality.BITONAL);
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality.COLOR);
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality.GRAY);
        SUPPORTED_IIIF_1_1_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality.NATIVE);

        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality.BITONAL);
        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality.COLOR);
        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality.DEFAULT);
        SUPPORTED_IIIF_2_0_QUALITIES.add(
                edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality.GRAY);

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
    private static HashMap<SourceFormat, Set<OutputFormat>> getFormats() {
        if (supportedFormats == null) {
            final Set<SourceFormat> sourceFormats = new HashSet<>();
            final Set<OutputFormat> outputFormats = new HashSet<>();
            // TODO: can we programmatically determine these?
            sourceFormats.add(SourceFormat.BMP);
            sourceFormats.add(SourceFormat.GIF);
            sourceFormats.add(SourceFormat.JPG);
            sourceFormats.add(SourceFormat.PNG);
            sourceFormats.add(SourceFormat.TIF);
            outputFormats.add(OutputFormat.GIF);
            outputFormats.add(OutputFormat.JPG);
            outputFormats.add(OutputFormat.PNG);
            outputFormats.add(OutputFormat.TIF);
            supportedFormats = new HashMap<>();
            for (SourceFormat sourceFormat : sourceFormats) {
                supportedFormats.put(sourceFormat, outputFormats);
            }
        }
        return supportedFormats;
    }

    /**
     * @param binaryName Name of one of the ExactImage binaries
     * @return
     */
    private static String getPath(String binaryName) {
        String path = Application.getConfiguration().
                getString(BINARIES_PATH_CONFIG_KEY);
        if (path != null) {
            path = StringUtils.stripEnd(path, File.separator) + File.separator +
                    binaryName;
        } else {
            path = binaryName;
        }
        return path;
    }

    /**
     * Quotes command-line parameters with spaces.
     *
     * @param path
     * @return
     */
    private static String quote(String path) {
        if (path.trim().contains(" ")) {
            path = "\"" + path + "\"";
        }
        return path;
    }

    @Override
    public Set<OutputFormat> getAvailableOutputFormats(SourceFormat sourceFormat) {
        Set<OutputFormat> formats = getFormats().get(sourceFormat);
        if (formats == null) {
            formats = new HashSet<>();
        }
        return formats;
    }

    /**
     * Gets the size of the given video by parsing the output of ffprobe.
     *
     * @param inputFile Source image
     * @param sourceFormat Format of the source image
     * @return
     * @throws ProcessorException
     */
    @Override
    public Dimension getSize(File inputFile, SourceFormat sourceFormat)
            throws ProcessorException {
        if (getAvailableOutputFormats(sourceFormat).size() < 1) {
            throw new UnsupportedSourceFormatException(sourceFormat);
        }

        final String glue = "|";
        final List<String> command = new ArrayList<>();
        command.add(getPath("edentify"));
        command.add("--format");
        command.add("%w" + glue + "%h");
        command.add(inputFile.getAbsolutePath());
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            logger.debug("Executing {}", StringUtils.join(pb.command(), " "));
            Process process = pb.start();

            String output = IOUtils.toString(process.getInputStream());
            process.waitFor();

            String[] parts = output.split(glue);
            if (parts.length == 2) {
                return new Dimension(Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()));
            } else {
                throw new ProcessorException("edentify failed to read size");
            }
        } catch (Exception e) {
            throw new ProcessorException(e.getMessage(), e);
        }
    }

    @Override
    public Set<ProcessorFeature> getSupportedFeatures(SourceFormat sourceFormat) {
        Set<ProcessorFeature> features = new HashSet<>();
        if (getAvailableOutputFormats(sourceFormat).size() > 0) {
            features.addAll(SUPPORTED_FEATURES);
        }
        return features;
    }

    @Override
    public Set<edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality>
    getSupportedIiif1_1Qualities(SourceFormat sourceFormat) {
        Set<edu.illinois.library.cantaloupe.resource.iiif.v1_1.Quality>
                qualities = new HashSet<>();
        if (getAvailableOutputFormats(sourceFormat).size() > 0) {
            qualities.addAll(SUPPORTED_IIIF_1_1_QUALITIES);
        }
        return qualities;
    }

    @Override
    public Set<edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality>
    getSupportedIiif2_0Qualities(SourceFormat sourceFormat) {
        Set<edu.illinois.library.cantaloupe.resource.iiif.v2_0.Quality>
                qualities = new HashSet<>();
        if (getAvailableOutputFormats(sourceFormat).size() > 0) {
            qualities.addAll(SUPPORTED_IIIF_2_0_QUALITIES);
        }
        return qualities;
    }

    @Override
    public void process(OperationList ops, SourceFormat sourceFormat,
                        Dimension fullSize, File inputFile,
                        OutputStream outputStream) throws ProcessorException {
        final Set<OutputFormat> availableOutputFormats =
                getAvailableOutputFormats(sourceFormat);
        if (getAvailableOutputFormats(sourceFormat).size() < 1) {
            throw new UnsupportedSourceFormatException(sourceFormat);
        } else if (!availableOutputFormats.contains(ops.getOutputFormat())) {
            throw new UnsupportedOutputFormatException();
        }

        final ByteArrayOutputStream outputBucket = new ByteArrayOutputStream();
        final ByteArrayOutputStream errorBucket = new ByteArrayOutputStream();
        try {
            final ProcessBuilder pb = getProcessBuilder(ops, fullSize,
                    inputFile);
            logger.debug("Executing {}", StringUtils.join(pb.command(), " "));
            final Process process = pb.start();

            new Thread(new StreamCopier(process.getInputStream(), outputBucket)).start();
            new Thread(new StreamCopier(process.getErrorStream(), errorBucket)).start();

            try {
                int code = process.waitFor();
                if (code != 0) {
                    logger.warn("econvert returned with code {}", code);
                    final String errorStr = errorBucket.toString();
                    if (errorStr != null && errorStr.length() > 0) {
                        throw new ProcessorException(errorStr);
                    }
                }
                final ByteArrayInputStream bais = new ByteArrayInputStream(
                        outputBucket.toByteArray());
                IOUtils.copy(bais, outputStream);
            } finally {
                process.getInputStream().close();
                process.getOutputStream().close();
                process.getErrorStream().close();
                process.destroy();
            }
        } catch (IOException | InterruptedException e) {
            String msg = e.getMessage();
            final String errorStr = errorBucket.toString();
            if (errorStr != null && errorStr.length() > 0) {
                msg += " (command output: " + msg + ")";
            }
            throw new ProcessorException(msg, e);
        }
    }

    /**
     * @param ops
     * @param fullSize The full size of the source image
     * @param inputFile
     * @return Command string
     */
    private ProcessBuilder getProcessBuilder(OperationList ops,
                                             Dimension fullSize,
                                             File inputFile) {
        return getProcessBuilder(ops, fullSize,
                quote(inputFile.getAbsolutePath()));
    }

    /**
     * @param ops
     * @param fullSize
     * @param inputArg Either an absolute pathname or <code>pipe:</code>
     * @return
     */
    private ProcessBuilder getProcessBuilder(OperationList ops,
                                             Dimension fullSize,
                                             String inputArg) {
        // econvert --input - --output jpeg:-
        final List<String> command = new ArrayList<>();
        command.add(getPath("econvert"));
        command.add("--input");
        command.add(inputArg); // TODO: use format hint
        command.add("--output");
        command.add("jpeg:-");

        for (Operation op : ops) {
            if (op instanceof Crop) {
                Crop crop = (Crop) op;
                if (!crop.isFull()) {
                    Rectangle cropArea = crop.getRectangle(fullSize);
                    // don't give an out-of-bounds crop area (is this necessary?)
                    //cropArea.width = Math.min(cropArea.width, fullSize.width - cropArea.x);
                    //cropArea.height = Math.min(cropArea.height, fullSize.height - cropArea.y);
                    command.add("--crop");
                    command.add(String.format("%d,%d,%d,%d", cropArea.x,
                            cropArea.y, cropArea.width, cropArea.height));
                }
            } else if (op instanceof Scale) {
                Scale scale = (Scale) op;
                if (scale.getMode() != Scale.Mode.FULL) {
                    command.add("--size");
                    if (scale.getMode() == Scale.Mode.ASPECT_FIT_WIDTH) {
                        // TODO: write this
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_HEIGHT) {
                        // TODO: write this
                    } else if (scale.getMode() == Scale.Mode.ASPECT_FIT_INSIDE) {
                        // TODO: write this
                    } else if (scale.getMode() == Scale.Mode.NON_ASPECT_FILL) {
                        command.add(String.format("%dx%d", scale.getWidth(),
                                scale.getHeight()));
                    } else if (scale.getPercent() != 0) {
                        int width = Math.round(fullSize.width * scale.getPercent());
                        int height = Math.round(fullSize.height * scale.getPercent());
                        command.add(String.format("%dx%d", width, height));
                    }
                }
            } else if (op instanceof Transpose) {
                Transpose transpose = (Transpose) op;
                switch (transpose) {
                    case HORIZONTAL:
                        command.add("--flop");
                        break;
                    case VERTICAL:
                        command.add("--flip");
                        break;
                }
            } else if (op instanceof Rotate) {
                Rotate rotate = (Rotate) op;
                if (rotate.getDegrees() > 0) {
                    command.add("--rotate");
                    command.add(rotate.getDegrees() + "");
                }
            } else if (op instanceof Filter) {
                Filter filter = (Filter) op;
                switch (filter) {
                    case BITONAL:
                        command.add("--colorspace");
                        command.add("BILEVEL");
                    case GRAY:
                        command.add("--colorspace");
                        command.add("GRAY");
                }
            }
        }

        command.add("--output");
        command.add(ops.getOutputFormat().getExtension() + ":-"); // TODO: extension won't work

        return new ProcessBuilder(command);
    }

}
