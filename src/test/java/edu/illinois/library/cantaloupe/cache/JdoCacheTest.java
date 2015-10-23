package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.CantaloupeTestCase;
import edu.illinois.library.cantaloupe.request.Identifier;
import edu.illinois.library.cantaloupe.request.Parameters;
import edu.illinois.library.cantaloupe.test.TestUtil;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.FileUtils;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import java.awt.Dimension;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class JdoCacheTest extends CantaloupeTestCase {

    JdoCache instance;

    private PersistenceManager getPersistenceManager() {
        PersistenceManagerFactory pmf = JdoCache.getPersistenceManagerFactory();
        return pmf.getPersistenceManager();
    }

    public void setUp() throws IOException {
        BaseConfiguration config = new BaseConfiguration();
        // use an in-memory H2 database
        config.setProperty("JdoCache.driver", "org.h2.Driver");
        config.setProperty("JdoCache.connection_string", "jdbc:h2:mem:test");
        config.setProperty("JdoCache.user", "sa");
        config.setProperty("JdoCache.password", "");
        config.setProperty("JdoCache.ttl_seconds", 0);
        Application.setConfiguration(config);

        instance = new JdoCache();

        PersistenceManager pm = getPersistenceManager();
        // persist some Images
        Parameters params = new Parameters("cats", "full", "full", "0",
                "default", "jpg");
        Image image = new Image();
        image.setParameters(params);
        image.setImage(FileUtils.readFileToByteArray(TestUtil.getFixture("jpg")));
        pm.makePersistent(image);
        params = new Parameters("dogs", "50,50,50,50", "pct:90",
                "0", "default", "jpg");
        image = new Image();
        image.setParameters(params);
        image.setImage(FileUtils.readFileToByteArray(TestUtil.getFixture("jpg")));
        pm.makePersistent(image);
        params = new Parameters("bunnies", "10,20,50,90", "40,",
                "15", "color", "png");
        image = new Image();
        image.setImage(FileUtils.readFileToByteArray(TestUtil.getFixture("jpg")));
        image.setParameters(params);
        pm.makePersistent(image);

        // persist some corresponding ImageInfos
        ImageInfo info = new ImageInfo();
        info.setIdentifier("cats");
        info.setWidth(50);
        info.setHeight(40);
        pm.makePersistent(info);
        info = new ImageInfo();
        info.setIdentifier("dogs");
        info.setWidth(500);
        info.setHeight(300);
        pm.makePersistent(info);
        info = new ImageInfo();
        info.setIdentifier("bunnies");
        info.setWidth(350);
        info.setHeight(240);
        pm.makePersistent(info);
    }

    /**
     * Clears the persistent store.
     */
    public void tearDown() {
        PersistenceManager pm = getPersistenceManager();
        JdoHelper.deleteAll(pm, Image.class, ImageInfo.class);
        pm.close();
    }

    public void testFlush() {
        PersistenceManager pm = getPersistenceManager();

        // assert that the Images and ImageInfos exist
        Query query = pm.newQuery(Image.class);
        try {
            List results = (List) query.execute();
            assertEquals(3, results.size());
        } finally {
            query.closeAll();
        }

        query = pm.newQuery(ImageInfo.class);
        try {
            List results = (List) query.execute();
            assertEquals(3, results.size());
        } finally {
            query.closeAll();
        }

        instance.flush();

        // assert that the Images and ImageInfos were flushed
        query = pm.newQuery(Image.class);
        try {
            List results = (List) query.execute();
            assertEquals(0, results.size());
        } finally {
            query.closeAll();
        }

        query = pm.newQuery(ImageInfo.class);
        try {
            List results = (List) query.execute();
            assertEquals(0, results.size());
        } finally {
            query.closeAll();
        }
    }

    public void testFlushWithParameters() {
        PersistenceManager pm = getPersistenceManager();

        Parameters params = new Parameters("cats", "full", "full", "0",
                "default", "jpg");
        instance.flush(params);

        Query query = pm.newQuery(Image.class);
        try {
            List results = (List) query.execute();
            assertEquals(2, results.size());
        } finally {
            query.closeAll();
        }

        query = pm.newQuery(ImageInfo.class);
        try {
            List results = (List) query.execute();
            assertEquals(2, results.size());
        } finally {
            query.closeAll();
        }
    }

    public void testFlushExpired() throws Exception {
        Application.getConfiguration().setProperty("JdoCache.ttl_seconds", 1);
        PersistenceManager pm = getPersistenceManager();

        Query query = pm.newQuery(Image.class);
        try {
            List results = (List) query.execute();
            assertEquals(3, results.size());
        } finally {
            query.closeAll();
        }
        query = pm.newQuery(ImageInfo.class);
        try {
            List results = (List) query.execute();
            assertEquals(3, results.size());
        } finally {
            query.closeAll();
        }

        // wait for the seed data to invalidate
        Thread.sleep(1500);

        // add some fresh entities
        Parameters params = new Parameters("bees", "full", "full", "0",
                "default", "jpg");
        Image image = new Image();
        image.setParameters(params);
        pm.makePersistent(image);

        ImageInfo info = new ImageInfo();
        info.setIdentifier("bees");
        info.setWidth(50);
        info.setHeight(40);
        pm.makePersistent(info);

        instance.flushExpired();

        // assert that only the expired Images and ImageInfos were flushed
        query = pm.newQuery(Image.class);
        try {
            List results = (List) query.execute();
            assertEquals(1, results.size());
        } finally {
            query.closeAll();
        }

        query = pm.newQuery(ImageInfo.class);
        try {
            List results = (List) query.execute();
            assertEquals(1, results.size());
        } finally {
            query.closeAll();
        }
    }

    public void testGetDimensionWithZeroTtl() throws IOException {
        // existing image
        try {
            Dimension actual = instance.getDimension(new Identifier("cats"));
            Dimension expected = new Dimension(50, 40);
            assertEquals(actual, expected);
        } catch (IOException e) {
            fail();
        }
        // nonexistent image
        assertNull(instance.getDimension(new Identifier("bogus")));
    }

    public void testGetDimensionWithNonZeroTtl() throws Exception {
        Application.getConfiguration().setProperty(JdoCache.TTL_CONFIG_KEY, 1);

        // wait for the seed data to invalidate
        Thread.sleep(1500);

        // add some fresh entities
        PersistenceManager pm = getPersistenceManager();
        Parameters params = new Parameters("bees", "full", "full", "0",
                "default", "jpg");
        Image image = new Image();
        image.setParameters(params);
        pm.makePersistent(image);

        ImageInfo info = new ImageInfo();
        info.setIdentifier("bees");
        info.setWidth(50);
        info.setHeight(40);
        pm.makePersistent(info);

        // existing, non-expired image
        try {
            Dimension actual = instance.getDimension(new Identifier("bees"));
            Dimension expected = new Dimension(50, 40);
            assertEquals(actual, expected);
        } catch (IOException e) {
            fail();
        }
        // existing, expired image
        assertNull(instance.getDimension(new Identifier("cats")));
        // nonexistent image
        assertNull(instance.getDimension(new Identifier("bogus")));
    }

    public void testGetImageInputStreamWithZeroTtl() {
        Parameters params = new Parameters("cats", "full", "full", "0",
                "default", "jpg");
        assertNotNull(instance.getImageInputStream(params));
    }

    public void testGetImageInputStreamWithNonzeroTtl() throws Exception {
        Application.getConfiguration().setProperty(JdoCache.TTL_CONFIG_KEY, 1);

        // wait for the seed data to invalidate
        Thread.sleep(1500);

        // add some fresh entities
        PersistenceManager pm = getPersistenceManager();
        Parameters params = new Parameters("bees", "full", "full", "0",
                "default", "jpg");
        Image image = new Image();
        image.setParameters(params);
        image.setImage(FileUtils.readFileToByteArray(TestUtil.getFixture("jpg")));
        pm.makePersistent(image);

        ImageInfo info = new ImageInfo();
        info.setIdentifier("bees");
        info.setWidth(50);
        info.setHeight(40);
        pm.makePersistent(info);

        // existing, non-expired image
        assertNotNull(instance.getImageInputStream(params));
        // existing, expired image
        assertNull(instance.getImageInputStream(
                Parameters.fromUri("cats/full/full/0/default.jpg")));
        // nonexistent image
        assertNull(instance.getImageInputStream(
                Parameters.fromUri("bogus/full/full/0/default.jpg")));
    }

    public void testGetImageOutputStream() throws Exception {
        Parameters params = new Parameters("cats", "full", "full", "0",
                "default", "jpg");
        assertNotNull(instance.getImageOutputStream(params));
    }

    public void testOldestValidDate() {
        // ttl = 0
        assertEquals(new Date(Long.MIN_VALUE), instance.oldestValidDate());
        // ttl = 50
        Application.getConfiguration().setProperty(JdoCache.TTL_CONFIG_KEY, 50);
        long expectedTime = Date.from(Instant.now().minus(Duration.ofSeconds(50))).getTime();
        long actualTime = instance.oldestValidDate().getTime();
        assertTrue(Math.abs(actualTime - expectedTime) < 100);
    }

    public void testPutDimension() throws IOException {
        Identifier identifier = new Identifier("birds");
        Dimension dimension = new Dimension(52, 52);
        instance.putDimension(identifier, dimension);
        assertEquals(dimension, instance.getDimension(identifier));
    }

}
