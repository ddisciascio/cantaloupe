package edu.illinois.library.cantaloupe.processor.imageio;

import edu.illinois.library.cantaloupe.image.Compression;
import edu.illinois.library.cantaloupe.test.BaseTest;
import edu.illinois.library.cantaloupe.test.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;

public class DICOMImageReaderTest extends BaseTest {

    private DICOMImageReader instance;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        instance = new DICOMImageReader(TestUtil.getImage("dcm"));
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        instance.dispose();
    }

    @Test
    public void testGetCompression() {
        assertEquals(Compression.UNCOMPRESSED, instance.getCompression(0));
    }

    @Test
    public void testGetIIOReader() {
        assertNotNull(instance.getIIOReader());
    }

    @Test
    public void testGetMetadata() throws Exception {
        assertNotNull(instance.getMetadata(0));
    }

    @Test
    public void testGetNumResolutions() throws Exception {
        assertEquals(1, instance.getNumResolutions());
    }

    @Test
    public void testGetSize() throws Exception {
        assertEquals(new Dimension(256, 120), instance.getSize());
    }

    @Test
    public void testGetSizeWithIndex() throws Exception {
        assertEquals(new Dimension(256, 120), instance.getSize(0));
    }

    @Test
    public void testGetTileSize() throws Exception {
        assertEquals(new Dimension(256, 120), instance.getTileSize(0));
    }

    @Test
    public void testPreferredIIOImplementations() {
        assertEquals(0, instance.preferredIIOImplementations().length);
    }

    @Test
    public void testRead() {
        // TODO: write this
    }

    @Test
    public void testReadWithArguments() {
        // TODO: write this
    }

    @Test
    public void testReadSmallestUsableSubimageReturningBufferedImage() {
        // TODO: write this
    }

    @Test
    public void testReadRendered() {
        // TODO: write this
    }

    @Test
    public void testReadRenderedWithArguments() {
        // TODO: write this
    }

    @Test
    public void testReadSmallestUsableSubimageReturningRenderedImage() {
        // TODO: write this
    }

}
