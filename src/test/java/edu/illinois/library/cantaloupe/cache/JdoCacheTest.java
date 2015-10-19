package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.request.Identifier;
import edu.illinois.library.cantaloupe.request.Parameters;
import junit.framework.TestCase;
import org.apache.commons.configuration.BaseConfiguration;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import java.awt.Dimension;
import java.io.IOException;
import java.util.List;

public class JdoCacheTest extends TestCase {

    JdoCache instance;

    private PersistenceManager getPersistenceManager() {
        PersistenceManagerFactory pmf = JdoCache.getPersistenceManagerFactory();
        return pmf.getPersistenceManager();
    }

    public void setUp() throws IOException {
        System.setProperty("java.awt.headless", "true");

        BaseConfiguration config = new BaseConfiguration();
        // use an in-memory H2 database
        config.setProperty("JdoCache.driver", "org.h2.Driver");
        config.setProperty("JdoCache.connection_string", "jdbc:h2:mem:test");
        config.setProperty("JdoCache.user", "sa");
        config.setProperty("JdoCache.password", "");
        config.setProperty("JdoCache.ttl_seconds", 5);
        Application.setConfiguration(config);

        instance = new JdoCache();
    }

    public void testFlush() throws Exception {
        PersistenceManager pm = getPersistenceManager();
        try {
            // persist an Image
            Parameters params = new Parameters("cats", "full", "full", "0",
                    "default", "jpg");
            Image image = new Image();
            image.setParameters(params);
            pm.makePersistent(image);
            // persist an ImageInfo
            ImageInfo info = new ImageInfo();
            info.setIdentifier("cats");
            info.setWidth(50);
            info.setHeight(40);
            pm.makePersistent(info);

            // assert that the Image and ImageInfo were saved
            Query query = pm.newQuery(Image.class);
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

            instance.flush();

            // assert that the Image and ImageInfo were flushed
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
        } finally {
            pm.close();
        }
    }

    public void testFlushWithParameters() throws Exception {
        // TODO: write this
    }

    public void testFlushExpired() throws Exception {
        // TODO: write this
    }

    public void testGetDimensionWithZeroTtl() throws Exception {
        // TODO: write this
    }

    public void testGetDimensionWithNonZeroTtl() throws Exception {
        // TODO: write this
    }

    public void testGetImageInputStreamWithZeroTtl() throws Exception {
        // TODO: write this
    }

    public void testGetImageInputStreamWithNonzeroTtl() throws Exception {
        // TODO: write this
    }

    public void testGetImageOutputStream() throws Exception {
        Parameters params = new Parameters("cats", "full", "full", "0",
                "default", "jpg");
        assertNotNull(instance.getImageOutputStream(params));
    }

    public void testPutDimension() throws IOException {
        Identifier identifier = new Identifier("cats");
        Dimension dimension = new Dimension(52, 52);
        instance.putDimension(identifier, dimension);
        assertEquals(dimension, instance.getDimension(identifier));
    }

}
