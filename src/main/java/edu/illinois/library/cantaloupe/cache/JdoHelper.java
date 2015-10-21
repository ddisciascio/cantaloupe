package edu.illinois.library.cantaloupe.cache;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import java.util.List;

abstract class JdoHelper {

    /**
     * @param pm
     * @param classes
     */
    public static void deleteAll(PersistenceManager pm, Class... classes) {
        for (Class clazz : classes) {
            Query query = pm.newQuery(clazz);
            try {
                List results = (List) query.execute();
                pm.deletePersistentAll(results);
            } finally {
                query.closeAll();
            }
        }
        /* TODO: why doesn't this work:
        PersistenceManager pm = getPersistenceManager();
        Set objects = pm.getManagedObjects(classes);
        if (objects != null) {
            pm.deletePersistentAll(objects);
        }
        */
    }

}
