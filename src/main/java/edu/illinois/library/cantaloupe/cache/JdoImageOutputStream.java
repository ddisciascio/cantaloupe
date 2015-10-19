package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.request.Parameters;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Transaction;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Buffers written image data and persists it via JDO when closed.
 */
class JdoImageOutputStream extends OutputStream {

    private ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private Parameters params;
    private PersistenceManagerFactory pmFactory;

    public JdoImageOutputStream(PersistenceManagerFactory pmf,
                                Parameters params) {
        this.pmFactory = pmf;
        this.params = params;
    }

    @Override
    public void close() throws IOException {
        PersistenceManager pm = pmFactory.getPersistenceManager();
        try {
            Transaction tx = pm.currentTransaction();
            tx.begin();
            Image image = new Image();
            image.setImage(outputStream.toByteArray());
            image.setParameters(params);
            pm.makePersistent(image);
            tx.commit();
        } finally {
            outputStream.close();
            pm.close();
        }
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        outputStream.write(b, off, len);
    }

}
