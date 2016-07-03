package edu.illinois.library.cantaloupe.processor.io;

import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

/**
 * Wraps an {@link IIOMetadata} instance for the purposes of ImageIO metadata
 * exchange, adding some convenient accessors to access specific metadata
 * types.
 */
public interface ImageIoMetadata {

    IIOMetadataNode getAsTree();

    /**
     * @return EXIF data, or null if none was found in the source metadata.
     */
    Object getExif();

    /**
     * @return IPTC data, or null if none was found in the source metadata.
     */
    Object getIptc();

    /**
     * @return XMP data, or null if none was found in the source metadata.
     */
    Object getXmp();

}