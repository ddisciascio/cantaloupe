package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.request.Identifier;
import edu.illinois.library.cantaloupe.request.Parameters;
import org.apache.commons.configuration.Configuration;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.datanucleus.metadata.PersistenceUnitMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;
import javax.jdo.Transaction;
import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Cache using JDO.
 */
class JdoCache implements Cache {

    private static final Logger logger = LoggerFactory.
            getLogger(JdoCache.class);

    private static final String CONNECTION_STRING_KEY = "JdoCache.connection_string";
    private static final String DRIVER_KEY = "JdoCache.driver";
    private static final String PASSWORD_KEY = "JdoCache.password";
    private static final String TTL_KEY = "JdoCache.ttl_seconds";
    private static final String USER_KEY = "JdoCache.user";

    private static PersistenceManagerFactory pmFactory;

    public static PersistenceManagerFactory getPersistenceManagerFactory() {
        if (pmFactory == null) {
            Configuration config = Application.getConfiguration();
            // Configure the persistence unit dynamically in lieu of persistence.xml
            PersistenceUnitMetaData pumd = new PersistenceUnitMetaData(
                    "dynamic-unit", "RESOURCE_LOCAL", null);
            pumd.addClassName(Image.class.getName());
            pumd.addClassName(ImageInfo.class.getName());
            pumd.setExcludeUnlistedClasses();
            pumd.addProperty("datanucleus.ConnectionDriverName",
                    config.getString(DRIVER_KEY));
            pumd.addProperty("datanucleus.ConnectionURL",
                    config.getString(CONNECTION_STRING_KEY));
            pumd.addProperty("datanucleus.ConnectionUserName",
                    config.getString(USER_KEY));
            pumd.addProperty("datanucleus.ConnectionPassword",
                    config.getString(PASSWORD_KEY));
            pumd.addProperty("datanucleus.autoCreateSchema", "true");

            pmFactory = new JDOPersistenceManagerFactory(pumd, null);
            logger.debug("persistence.xml: {}", pumd.toString());
            logger.info("Using {}", pmFactory.getConnectionDriverName());
            logger.info("URL: {}", pmFactory.getConnectionURL());
        }
        return pmFactory;
    }

    @Override
    public void flush() {
        flushImages();
        flushInfos();
        logger.info("Flushed the cache");
    }

    private void flushImages() {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        try {
            Transaction tx = pm.currentTransaction();
            tx.begin();
            Set images = pm.getManagedObjects(Image.class);
            pm.deletePersistentAll(images);
            tx.commit();
        } finally {
            pm.close();
        }
    }

    private void flushInfos() {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        try {
            Transaction tx = pm.currentTransaction();
            tx.begin();
            Set images = pm.getManagedObjects(Image.class);
            pm.deletePersistentAll(images);
            tx.commit();
        } finally {
            pm.close();
        }
    }

    @Override
    public void flush(Parameters params) {
        flushImage(params);
        flushInfo(params.getIdentifier());
        logger.info("Flushed {}", params.toString());
    }

    private void flushImage(Parameters params) {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Query query = pm.newQuery(Image.class);
        query.setFilter("params == paramsParam");
        query.declareParameters("String paramsParam");
        try {
            List results = (List) query.execute(params.toString());
            pm.deletePersistentAll(results);
        } finally {
            query.closeAll();
        }
    }

    private void flushInfo(Identifier identifier) {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Query query = pm.newQuery(ImageInfo.class);
        query.setFilter("identifier == identifierParam");
        query.declareParameters("String identifierParam");
        try {
            List results = (List) query.execute(identifier.toString());
            pm.deletePersistentAll(results);
        } finally {
            query.closeAll();
        }
    }

    @Override
    public void flushExpired() {
        Class[] classes = {Image.class, ImageInfo.class};
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        for (Class clazz : classes) {
            Query query = pm.newQuery(clazz);
            query.setFilter("lastModified < dateParam");
            query.declareParameters("Date dateParam");
            try {
                List results = (List) query.execute(oldestValidDate());
                pm.deletePersistentAll(results);
            } finally {
                query.closeAll();
            }
        }
        logger.info("Flushed expired entities from the cache");
    }

    @Override
    public Dimension getDimension(Identifier identifier) throws IOException {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Query query = pm.newQuery(ImageInfo.class);
        query.setFilter("identifier == identifierParam");
        query.declareParameters("String identifierParam");
        try {
            List results = (List) query.execute(identifier.toString());
            if (!results.isEmpty()) {
                ImageInfo info = (ImageInfo) results.get(0);
                return new Dimension(info.getWidth(), info.getHeight());
            }
        } finally {
            query.closeAll();
        }
        return null;
    }

    @Override
    public InputStream getImageInputStream(Parameters params) {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Query query = pm.newQuery(Image.class);
        query.setFilter("identifier == identifierParam");
        query.declareParameters("String identifierParam");
        try {
            List results = (List) query.execute(params.getIdentifier().toString());
            if (!results.isEmpty()) {
                Image image = (Image) results.get(0);
                return new ByteArrayInputStream(image.getImage());
            }
        } finally {
            query.closeAll();
        }
        return null;
    }

    @Override
    public OutputStream getImageOutputStream(Parameters params)
            throws IOException {
        return new JdoImageOutputStream(getPersistenceManagerFactory(), params);
    }

    @Override
    public void putDimension(Identifier identifier, Dimension dimension)
            throws IOException {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        try {
            ImageInfo info = new ImageInfo();
            info.setIdentifier(identifier.toString());
            info.setWidth(dimension.width);
            info.setHeight(dimension.height);
            pm.makePersistent(info);
        } finally {
            pm.close();
        }
    }

    private Date oldestValidDate() {
        Configuration config = Application.getConfiguration();
        final Instant oldestInstant = Instant.now().
                minus(Duration.ofSeconds(config.getLong(TTL_KEY, 0)));
        return Date.from(oldestInstant);
    }

}
