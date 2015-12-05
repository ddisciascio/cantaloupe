package edu.illinois.library.cantaloupe.resource.iiif.v2_0;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.ImageServerApplication;
import edu.illinois.library.cantaloupe.cache.Cache;
import edu.illinois.library.cantaloupe.cache.CacheFactory;
import edu.illinois.library.cantaloupe.image.OperationList;
import edu.illinois.library.cantaloupe.image.OutputFormat;
import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.processor.FileProcessor;
import edu.illinois.library.cantaloupe.processor.Processor;
import edu.illinois.library.cantaloupe.processor.ProcessorFactory;
import edu.illinois.library.cantaloupe.processor.StreamProcessor;
import edu.illinois.library.cantaloupe.processor.UnsupportedSourceFormatException;
import edu.illinois.library.cantaloupe.resolver.FileResolver;
import edu.illinois.library.cantaloupe.resolver.Resolver;
import edu.illinois.library.cantaloupe.resolver.ResolverFactory;
import edu.illinois.library.cantaloupe.resolver.StreamResolver;
import edu.illinois.library.cantaloupe.resource.AbstractResource;
import edu.illinois.library.cantaloupe.resource.CachedImageRepresentation;
import edu.illinois.library.cantaloupe.resource.EndpointDisabledException;
import edu.illinois.library.cantaloupe.resource.ImageRepresentation;
import edu.illinois.library.cantaloupe.resource.PayloadTooLargeException;
import org.restlet.data.MediaType;
import org.restlet.representation.OutputRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * Handles IIIF image requests.
 *
 * @see <a href="http://iiif.io/api/image/2.0/#image-request-parameters">Image
 * Request Operations</a>
 */
public class ImageResource extends AbstractResource {

    private static Logger logger = LoggerFactory.getLogger(ImageResource.class);

    @Override
    protected void doInit() throws ResourceException {
        if (!Application.getConfiguration().
                getBoolean("endpoint.iiif.2.0.enabled", true)) {
            throw new EndpointDisabledException();
        }
        super.doInit();
        getResponseCacheDirectives().addAll(getCacheDirectives());
    }

    /**
     * Responds to IIIF Image requests.
     *
     * @return OutputRepresentation
     * @throws Exception
     */
    @Get
    public OutputRepresentation doGet() throws Exception {
        Map<String,Object> attrs = this.getRequest().getAttributes();
        // Assemble the URI parameters into a Parameters object
        Parameters params = new Parameters(
                (String) attrs.get("identifier"),
                (String) attrs.get("region"),
                (String) attrs.get("size"),
                (String) attrs.get("rotation"),
                (String) attrs.get("quality"),
                (String) attrs.get("format"));
        OperationList ops = params.toOperationList();

        // If we don't need to resolve first, and are using a cache, and the
        // cache contains an image matching the request, skip all the setup and
        // just return the cached image.
        if (!Application.getConfiguration().
                getBoolean(RESOLVE_FIRST_CONFIG_KEY, true)) {
            Cache cache = CacheFactory.getInstance();
            if (cache != null) {
                InputStream inputStream = cache.getImageInputStream(ops);
                if (inputStream != null) {
                    this.addLinkHeader(params);
                    return new CachedImageRepresentation(
                            new MediaType(params.getOutputFormat().getMediaType()),
                            ops, inputStream);
                }
            }
        }

        Resolver resolver = ResolverFactory.getResolver();
        // Determine the format of the source image
        SourceFormat sourceFormat = SourceFormat.UNKNOWN;
        try {
            sourceFormat = resolver.getSourceFormat(ops.getIdentifier());
        } catch (FileNotFoundException e) {
            if (Application.getConfiguration().
                    getBoolean(PURGE_MISSING_CONFIG_KEY, false)) {
                // if the image was not found, purge it from the cache
                final Cache cache = CacheFactory.getInstance();
                if (cache != null) {
                    cache.purge(ops.getIdentifier());
                }
            }
            throw e;
        }
        if (sourceFormat.equals(SourceFormat.UNKNOWN)) {
            throw new UnsupportedSourceFormatException();
        }
        // Obtain an instance of the processor assigned to that format in
        // the config file
        Processor proc = ProcessorFactory.getProcessor(sourceFormat);

        // Find out whether the processor supports that source format by
        // asking it whether it offers any output formats for it
        Set<OutputFormat> availableOutputFormats =
                proc.getAvailableOutputFormats(sourceFormat);
        if (!availableOutputFormats.contains(ops.getOutputFormat())) {
            String msg = String.format("%s does not support the \"%s\" output format",
                    proc.getClass().getSimpleName(),
                    ops.getOutputFormat().getExtension());
            logger.warn(msg + ": " + this.getReference());
            throw new UnsupportedSourceFormatException(msg);
        }

        this.addLinkHeader(params);

        return getRepresentation(ops, sourceFormat, resolver, proc);
    }

    private void addLinkHeader(Parameters params) {
        this.addHeader("Link", String.format("<%s%s/%s>;rel=\"canonical\"",
                getPublicRootRef().toString(),
                ImageServerApplication.IIIF_2_0_PATH, params.toString()));
    }

    private OutputRepresentation getRepresentation(OperationList ops,
                                                   SourceFormat sourceFormat,
                                                   Resolver resolver,
                                                   Processor proc)
            throws Exception {
        final MediaType mediaType = new MediaType(
                ops.getOutputFormat().getMediaType());
        final long maxAllowedSize = Application.getConfiguration().
                getLong("max_pixels", 0);

        // FileResolver -> StreamProcessor: OK, using FileInputStream
        // FileResolver -> FileProcessor: OK, using File
        // StreamResolver -> StreamProcessor: OK, using InputStream
        // StreamResolver -> FileProcessor: NOPE
        if (!(resolver instanceof FileResolver) &&
                !(proc instanceof StreamProcessor)) {
            // FileProcessors can't work with StreamResolvers
            throw new UnsupportedSourceFormatException(
                    String.format("%s is not compatible with %s",
                            proc.getClass().getSimpleName(),
                            resolver.getClass().getSimpleName()));
        } else if (resolver instanceof FileResolver &&
                proc instanceof FileProcessor) {
            logger.debug("Using {} as a FileProcessor",
                    proc.getClass().getSimpleName());
            final FileProcessor fproc = (FileProcessor) proc;
            final File inputFile = ((FileResolver) resolver).
                    getFile(ops.getIdentifier());
            final Dimension fullSize = fproc.getSize(inputFile, sourceFormat);
            final Dimension effectiveSize = ops.getResultingSize(fullSize);
            if (maxAllowedSize > 0 &&
                    effectiveSize.width * effectiveSize.height > maxAllowedSize) {
                throw new PayloadTooLargeException();
            }
            return new ImageRepresentation(mediaType, sourceFormat, fullSize,
                    ops, inputFile);
        } else if (resolver instanceof StreamResolver) {
            logger.debug("Using {} as a StreamProcessor",
                    proc.getClass().getSimpleName());
            final StreamResolver sres = (StreamResolver) resolver;
            if (proc instanceof StreamProcessor) {
                final StreamProcessor sproc = (StreamProcessor) proc;
                InputStream inputStream = sres.
                        getInputStream(ops.getIdentifier());
                final Dimension fullSize = sproc.getSize(inputStream,
                        sourceFormat);
                final Dimension effectiveSize = ops.getResultingSize(fullSize);
                if (maxAllowedSize > 0 &&
                        effectiveSize.width * effectiveSize.height > maxAllowedSize) {
                    throw new PayloadTooLargeException();
                }
                // avoid reusing the stream
                inputStream = sres.getInputStream(ops.getIdentifier());
                return new ImageRepresentation(mediaType, sourceFormat,
                        fullSize, ops, inputStream);
            }
        }
        return null; // should never happen
    }

}
