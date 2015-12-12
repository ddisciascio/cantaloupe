package edu.illinois.library.cantaloupe.resolver;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.image.Identifier;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class HttpResolver implements StreamResolver {

    private static Logger logger = LoggerFactory.getLogger(HttpResolver.class);

    public static final String BASIC_AUTH_SECRET_CONFIG_KEY =
            "HttpResolver.auth.basic.secret";
    public static final String BASIC_AUTH_USERNAME_CONFIG_KEY =
            "HttpResolver.auth.basic.username";
    public static final String LOOKUP_SCRIPT_CONFIG_KEY =
            "HttpResolver.ScriptLookupStrategy.script";
    public static final String LOOKUP_STRATEGY_CONFIG_KEY =
            "HttpResolver.lookup_strategy";
    public static final String PATH_SEPARATOR_CONFIG_KEY =
            "HttpResolver.path_separator";
    public static final String URL_PREFIX_CONFIG_KEY =
            "HttpResolver.BasicLookupStrategy.url_prefix";
    public static final String URL_SUFFIX_CONFIG_KEY =
            "HttpResolver.BasicLookupStrategy.url_suffix";

    private static final Set<String> SUPPORTED_SCRIPT_EXTENSIONS =
            new HashSet<>();

    private static Client client;

    // Caches the lookup script for improved performance.
    private static String lookupScriptContents;

    // lock object for synchronization
    private final Object lock = new Object();

    static {
        SUPPORTED_SCRIPT_EXTENSIONS.add("rb");

        List<Protocol> protocols = new ArrayList<>();
        protocols.add(Protocol.HTTP);
        protocols.add(Protocol.HTTPS);
        client = new Client(protocols);
    }

    /**
     * Passes the given identifier to a function in the given script.
     *
     * @param identifier
     * @param script
     * @return Pathname of the image file corresponding to the given identifier,
     * as reported by the lookup script.
     * @throws IOException If the lookup script configuration key is undefined
     * @throws ScriptException If the script failed to execute
     * @throws ScriptException If the script is of an unsupported type
     */
    public String executeLookupScript(Identifier identifier, File script)
            throws IOException, ScriptException {
        final String extension = FilenameUtils.getExtension(script.getName());

        if (SUPPORTED_SCRIPT_EXTENSIONS.contains(extension)) {
            logger.debug("Using lookup script: {}", script);
            if (lookupScriptContents == null) {
                synchronized (lock) {
                    lookupScriptContents = FileUtils.readFileToString(script);
                }
            }
            final ScriptEngineManager manager = new ScriptEngineManager();

            switch (extension) {
                case "rb":
                    final String idParam = StringUtils.replace(
                            identifier.toString(), "'", "\\'");
                    final String statement = String.format("%s\nget_url('%s')",
                            lookupScriptContents, idParam);
                    final ScriptEngine engine = manager.getEngineByName("jruby");
                    final long msec = System.currentTimeMillis();
                    final String result = (String) engine.eval(statement);
                    logger.debug("Lookup function execution time: {} msec",
                            System.currentTimeMillis() - msec);
                    return result;
            }
        }
        throw new ScriptException("Unsupported script type: " + extension);
    }

    @Override
    public InputStream getInputStream(final Identifier identifier)
            throws IOException {
        Reference url = getUrl(identifier);
        logger.debug("Resolved {} to {}", identifier, url);
        ClientResource resource = newClientResource(url);
        resource.setNext(client);
        try {
            return resource.get().getStream();
        } catch (ResourceException e) {
            if (e.getStatus().equals(Status.CLIENT_ERROR_NOT_FOUND) ||
                    e.getStatus().equals(Status.CLIENT_ERROR_GONE)) {
                throw new FileNotFoundException(e.getMessage());
            } else if (e.getStatus().equals(Status.CLIENT_ERROR_FORBIDDEN)) {
                throw new AccessDeniedException(e.getMessage());
            } else {
                throw new IOException(e.getMessage());
            }
        }
    }

    @Override
    public SourceFormat getSourceFormat(final Identifier identifier)
            throws IOException {
        SourceFormat format = getSourceFormatFromIdentifier(identifier);
        if (format == SourceFormat.UNKNOWN) {
            format = getSourceFormatFromServer(identifier);
        }
        getInputStream(identifier); // throws IOException if not found etc.
        return format;
    }

    public Reference getUrl(Identifier identifier) throws IOException {
        final Configuration config = Application.getConfiguration();
        identifier = replacePathSeparators(identifier);

        switch (config.getString(LOOKUP_STRATEGY_CONFIG_KEY)) {
            case "BasicLookupStrategy":
                return getUrlWithBasicStrategy(identifier);
            case "ScriptLookupStrategy":
                try {
                    return getUrlWithScriptStrategy(identifier);
                } catch (ScriptException e) {
                    logger.error(e.getMessage(), e);
                    throw new IOException(e);
                }
            default:
                throw new IOException(LOOKUP_STRATEGY_CONFIG_KEY +
                        " is invalid or not set");
        }
    }

    /**
     * @param identifier
     * @return A source format, or {@link SourceFormat#UNKNOWN} if unknown.
     */
    private SourceFormat getSourceFormatFromIdentifier(Identifier identifier) {
        // try to get the source format based on a filename extension in the
        // identifier
        String idStr = identifier.toString().toLowerCase();
        String extension = null;
        SourceFormat sourceFormat = SourceFormat.UNKNOWN;
        int i = idStr.lastIndexOf('.');
        if (i > 0) {
            extension = idStr.substring(i + 1);
        }
        if (extension != null) {
            for (SourceFormat enumValue : SourceFormat.values()) {
                if (enumValue.getExtensions().contains(extension)) {
                    sourceFormat = enumValue;
                    break;
                }
            }
        }
        return sourceFormat;
    }

    /**
     * Issues an HTTP HEAD request and checks the Content-Type header in the
     * response to determine the source format.
     *
     * @param identifier
     * @return A source format, or {@link SourceFormat#UNKNOWN} if unknown.
     * @throws IOException
     */
    private SourceFormat getSourceFormatFromServer(Identifier identifier)
            throws IOException {
        SourceFormat sourceFormat = SourceFormat.UNKNOWN;
        String contentType = "";
        Reference url = getUrl(identifier);
        try {
            Client client = new Client(new Context(), Protocol.HTTP);
            ClientResource resource = new ClientResource(url);
            resource.setNext(client);
            resource.head();

            contentType = resource.getResponse().getHeaders().
                    getFirstValue("Content-Type", true);
            if (contentType != null) {
                sourceFormat = SourceFormat.
                        getSourceFormat(new MediaType(contentType));
            }
        } catch (ResourceException e) {
            // nothing we can do but log it
            if (contentType.length() > 0) {
                logger.debug("Failed to determine source format based on a " +
                        "Content-Type of {}", contentType);
            } else {
                logger.debug("Failed to determine source format (missing " +
                        "Content-Type at {})", url);
            }
        }
        return sourceFormat;
    }

    private Reference getUrlWithBasicStrategy(final Identifier identifier) {
        final Configuration config = Application.getConfiguration();
        final String prefix = config.getString(URL_PREFIX_CONFIG_KEY, "");
        final String suffix = config.getString(URL_SUFFIX_CONFIG_KEY, "");
        return new Reference(prefix + identifier.toString() + suffix);
    }

    /**
     * @param identifier
     * @return
     * @throws FileNotFoundException If a script does not exist
     * @throws IOException
     * @throws ScriptException If the script fails to execute
     * @throws ScriptException If the script is of an unsupported type
     */
    private Reference getUrlWithScriptStrategy(Identifier identifier)
            throws IOException, ScriptException {
        final Configuration config = Application.getConfiguration();
        // The script name may be an absolute path or a filename.
        final String scriptValue = config.
                getString(LOOKUP_SCRIPT_CONFIG_KEY);
        File script = new File(scriptValue);
        if (!script.isAbsolute()) {
            // Search for it in the same folder as the application
            // config (if available), or the current working
            // directory if not.
            final File configFile = Application.getConfigurationFile();
            if (configFile != null) {
                script = new File(configFile.getParent() + "/" +
                        script.getName());
            } else {
                script = new File("./" + script.getName());
            }
        }
        if (!script.exists()) {
            throw new FileNotFoundException("Does not exist: " +
                    script.getAbsolutePath());
        }
        return new Reference(executeLookupScript(identifier, script));
    }

    /**
     * Factory method.
     *
     * @param url
     * @return New ClientResource respecting HttpResolver configuration
     * options.
     */
    private ClientResource newClientResource(final Reference url) {
        final ClientResource resource = new ClientResource(url);
        final Configuration config = Application.getConfiguration();
        final String username = config.getString(BASIC_AUTH_USERNAME_CONFIG_KEY, "");
        final String secret = config.getString(BASIC_AUTH_SECRET_CONFIG_KEY, "");
        if (username.length() > 0 && secret.length() > 0) {
            resource.setChallengeResponse(ChallengeScheme.HTTP_BASIC,
                    username, secret);
        }
        return resource;
    }

    /**
     * Some web servers have issues dealing with encoded slashes (%2F) in URL
     * identifiers. This method enables the use of an alternate string as a
     * path separator via {@link #PATH_SEPARATOR_CONFIG_KEY}.
     * #
     * @param identifier
     * @return
     */
    private Identifier replacePathSeparators(final Identifier identifier) {
        final Configuration config = Application.getConfiguration();
        String idStr = identifier.toString();
        final String separator = config.getString(PATH_SEPARATOR_CONFIG_KEY, "");
        if (separator.length() > 0) {
            idStr = StringUtils.replace(idStr, separator, "/");
        }
        return new Identifier(idStr);
    }

}
