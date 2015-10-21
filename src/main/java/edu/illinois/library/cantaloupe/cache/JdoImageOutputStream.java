package edu.illinois.library.cantaloupe.cache;

import edu.illinois.library.cantaloupe.request.Parameters;

import javax.jdo.PersistenceManager;
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
    private PersistenceManager persistenceManager;

    public JdoImageOutputStream(PersistenceManager pm, Parameters params) {
        this.persistenceManager = pm;
        this.params = params;
    }

    @Override
    public void close() throws IOException {
        Transaction tx = persistenceManager.currentTransaction();
        try {
            tx.begin();
            Image image = new Image();
            image.setImage(outputStream.toByteArray());
            image.setParameters(params);
            persistenceManager.makePersistent(image);
            tx.commit();
        } finally {
            if (tx.isActive()) {
                tx.rollback();
            }
            outputStream.close();
            persistenceManager.close();
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
