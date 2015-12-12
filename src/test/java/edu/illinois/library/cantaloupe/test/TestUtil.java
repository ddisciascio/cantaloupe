package edu.illinois.library.cantaloupe.test;

import edu.illinois.library.cantaloupe.image.Identifier;
import edu.illinois.library.cantaloupe.image.OperationList;
import edu.illinois.library.cantaloupe.image.OutputFormat;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class TestUtil {

    public static File getCurrentWorkingDirectory() throws IOException {
        File directory = new File(".");
        return directory.getCanonicalFile();
    }

    public static File getFixture(String filename) throws IOException {
        return new File(getFixturePath() + File.separator + filename);
    }

    public static Path getFixturePath() throws IOException {
        return Paths.get(getCurrentWorkingDirectory().getAbsolutePath(),
                "src", "test", "resources");
    }

    public static Integer getOpenPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static File getTempFolder() throws IOException {
        return new File(System.getProperty("java.io.tmpdir"));
    }

    /**
     * @return Dummy OperationList object with no operations added.
     */
    public static OperationList newOperationList() {
        OperationList ops = new OperationList();
        ops.setIdentifier(new Identifier("dummy"));
        ops.setOutputFormat(OutputFormat.JPG);
        return ops;
    }

}
