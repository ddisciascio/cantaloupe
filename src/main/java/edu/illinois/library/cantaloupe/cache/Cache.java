package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.request.Identifier;
import edu.illinois.library.cantaloupe.request.Parameters;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface to be implemented by all caches. Implementation instances will be
 * Singletons and must be thread-safe.
 */
public interface Cache {

    /**
     * Deletes the entire contents of the cache.
     *
     * @throws IOException
     */
    void flush() throws IOException;

    /**
     * Deletes the cached image and dimensions corresponding to the given
     * parameters.
     *
     * @param params
     * @throws IOException
     */
    void flush(Parameters params) throws IOException;

    /**
     * Deletes expired images and dimensions from the cache.
     *
     * @throws IOException
     */
    void flushExpired() throws IOException;

    /**
     * <p>Attempts to return a stream for reading a cached image.</p>
     *
     * <p>If a cached image corresponding to the given parameters exists
     * but is expired, implementations should delete it and return null.</p>
     *
     * @param params Request parameters
     * @return An input stream corresponding to the given parameters, or null
     * if a non-expired image corresponding to the given parameters does not
     * exist in the cache.
     */
    InputStream getImageInputStream(Parameters params);

    /**
     * <p>Attempts to read and return cached dimension information.</p>
     *
     * <p>If a cached dimension corresponding to the given identifier exists
     * but is expired, implementations should delete it and return null.</p>
     *
     * @param identifier Image identifier
     * @return Dimension corresponding to the given identifier, or null if no
     * non-expired dimension exists in the cache.
     */
    Dimension getDimension(Identifier identifier) throws IOException;

    /**
     * @param params IIIF request parameters
     * @return OutputStream pointed at the cache to which an image
     * corresponding to the supplied parameters can be written.
     * @throws IOException
     */
    OutputStream getImageOutputStream(Parameters params) throws IOException;

    /**
     * Adds an image's dimension information to the cache.
     *
     * @param identifier IIIF identifier
     * @param size Dimension containing width and height in pixels.
     */
    void putDimension(Identifier identifier, Dimension size) throws IOException;

}
