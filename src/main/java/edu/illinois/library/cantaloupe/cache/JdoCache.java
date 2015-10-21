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

/**
 * Cache using JDO.
 */
class JdoCache implements Cache {

    private static final Logger logger = LoggerFactory.
            getLogger(JdoCache.class);

    public static final String CONNECTION_STRING_CONFIG_KEY = "JdoCache.connection_string";
    public static final String DRIVER_CONFIG_KEY = "JdoCache.driver";
    public static final String PASSWORD_CONFIG_KEY = "JdoCache.password";
    public static final String TTL_CONFIG_KEY = "JdoCache.ttl_seconds";
    public static final String USER_CONfIG_KEY = "JdoCache.user";

    private static PersistenceManagerFactory pmFactory;

    public static synchronized PersistenceManagerFactory getPersistenceManagerFactory() {
        if (pmFactory == null) {
            Configuration config = Application.getConfiguration();
            // Configure the persistence unit dynamically in lieu of persistence.xml
            PersistenceUnitMetaData pumd = new PersistenceUnitMetaData(
                    "dynamic-unit", "RESOURCE_LOCAL", null);
            pumd.addClassName(Image.class.getName());
            pumd.addClassName(ImageInfo.class.getName());
            pumd.setExcludeUnlistedClasses();
            pumd.addProperty("datanucleus.ConnectionDriverName",
                    config.getString(DRIVER_CONFIG_KEY));
            pumd.addProperty("datanucleus.ConnectionURL",
                    config.getString(CONNECTION_STRING_CONFIG_KEY));
            pumd.addProperty("datanucleus.ConnectionUserName",
                    config.getString(USER_CONfIG_KEY));
            pumd.addProperty("datanucleus.ConnectionPassword",
                    config.getString(PASSWORD_CONFIG_KEY));
            pumd.addProperty("datanucleus.autoCreateSchema", "true");
            pumd.addProperty("datanucleus.identifier.case", "LowerCase");

            pmFactory = new JDOPersistenceManagerFactory(pumd, null);
            logger.debug("persistence.xml: {}", pumd.toString());
            logger.info("Using {}", pmFactory.getConnectionDriverName());
            logger.info("URL: {}", pmFactory.getConnectionURL());
        }
        return pmFactory;
    }

    @Override
    public void flush() {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();
            JdoHelper.deleteAll(pm, Image.class, ImageInfo.class);
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            pm.close();
        }
        logger.info("Flushed the cache");
    }

    @Override
    public void flush(Parameters params) {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        try {
            tx.begin();
            Query query = pm.newQuery(Image.class);
            query.setFilter("parameters == params");
            query.declareParameters("String params");
            try {
                List results = (List) query.execute(params.toString());
                pm.deletePersistentAll(results);
            } finally {
                query.closeAll();
            }

            query = pm.newQuery(ImageInfo.class);
            query.setFilter("identifier == identifierParam");
            query.declareParameters("String identifierParam");
            try {
                List results = (List) query.execute(params.getIdentifier().toString());
                pm.deletePersistentAll(results);
            } finally {
                query.closeAll();
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            pm.close();
        }
        logger.info("Flushed {}", params.toString());
    }

    @Override
    public void flushExpired() {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        Class[] classes = {Image.class, ImageInfo.class};
        try {
            tx.begin();
            for (Class clazz : classes) {
                Query query = pm.newQuery(clazz);
                query.setFilter("lastModified < date");
                query.declareParameters("java.util.Date date");
                try {
                    List results = (List) query.execute(oldestValidDate());
                    pm.deletePersistentAll(results);
                } finally {
                    query.closeAll();
                }
            }
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            pm.close();
        }
        logger.info("Flushed expired entities from the cache");
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public Dimension getDimension(Identifier identifier) throws IOException {
        Dimension dimension = null;
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Query query = pm.newQuery(ImageInfo.class);
        query.setFilter("identifier == identifierParam");
        query.declareParameters("String identifierParam");
        try {
            List<ImageInfo> results = (List<ImageInfo>) query.execute(identifier.toString());
            if (!results.isEmpty()) {
                ImageInfo info = results.get(0);
                if (info.getLastModified().before(oldestValidDate())) {
                    pm.deletePersistent(info);
                } else {
                    dimension = new Dimension(info.getWidth(), info.getHeight());
                }
            }
        } finally {
            query.closeAll();
        }
        return dimension;
    }

    @Override
    public InputStream getImageInputStream(Parameters params) {
        ByteArrayInputStream bais = null;
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Query query = pm.newQuery(Image.class);
        query.setFilter("parameters == params");
        query.declareParameters("String params");
        try {
            List results = (List) query.execute(params.toString());
            if (!results.isEmpty()) {
                Image image = (Image) results.get(0);
                if (image.getLastModified().before(oldestValidDate())) {
                    pm.deletePersistent(image);
                } else {
                    bais = new ByteArrayInputStream(image.getImage());
                }
            }
        } finally {
            query.closeAll();
        }
        return bais;
    }

    @Override
    public OutputStream getImageOutputStream(Parameters params)
            throws IOException {
        return new JdoImageOutputStream(
                getPersistenceManagerFactory().getPersistenceManager(), params);
    }

    @Override
    public void putDimension(Identifier identifier, Dimension dimension)
            throws IOException {
        PersistenceManager pm = getPersistenceManagerFactory().getPersistenceManager();
        Transaction tx = pm.currentTransaction();
        try {
            ImageInfo info = new ImageInfo();
            info.setIdentifier(identifier.toString());
            info.setWidth(dimension.width);
            info.setHeight(dimension.height);
            pm.makePersistent(info);
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            pm.close();
        }
    }

    public Date oldestValidDate() {
        Configuration config = Application.getConfiguration();
        final long ttl = config.getLong(TTL_CONFIG_KEY, 0);
        if (ttl > 0) {
            Instant oldestInstant = Instant.now().minus(Duration.ofSeconds(ttl));
            return Date.from(oldestInstant);
        }
        return new Date(Long.MIN_VALUE);
    }

}
