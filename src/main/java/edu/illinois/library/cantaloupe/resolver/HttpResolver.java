package edu.illinois.library.cantaloupe.resolver;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.image.Identifier;
import edu.illinois.library.cantaloupe.script.ScriptEngine;
import edu.illinois.library.cantaloupe.script.ScriptEngineFactory;
import org.apache.commons.configuration.Configuration;
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

import javax.script.ScriptException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;

class HttpResolver extends AbstractResolver implements ChannelResolver {

    private static class HttpChannelSource implements ChannelSource {

        private final Client client = new Client(
                Arrays.asList(Protocol.HTTP, Protocol.HTTPS));
        private final Reference url;

        public HttpChannelSource(Reference url) {
            this.url = url;
        }

        @Override
        public ReadableByteChannel newChannel() throws IOException {
            ClientResource resource = newClientResource(url);
            resource.setNext(client);
            try {
                return resource.get().getChannel();
            } catch (ResourceException e) {
                throw new IOException(e.getMessage(), e);
            }
        }

    }

    private static Logger logger = LoggerFactory.getLogger(HttpResolver.class);

    public static final String BASIC_AUTH_SECRET_CONFIG_KEY =
            "HttpResolver.auth.basic.secret";
    public static final String BASIC_AUTH_USERNAME_CONFIG_KEY =
            "HttpResolver.auth.basic.username";
    public static final String LOOKUP_STRATEGY_CONFIG_KEY =
            "HttpResolver.lookup_strategy";
    public static final String URL_PREFIX_CONFIG_KEY =
            "HttpResolver.BasicLookupStrategy.url_prefix";
    public static final String URL_SUFFIX_CONFIG_KEY =
            "HttpResolver.BasicLookupStrategy.url_suffix";

    /**
     * Factory method.
     *
     * @param url
     * @return New ClientResource respecting HttpResolver configuration
     * options.
     */
    private static ClientResource newClientResource(final Reference url) {
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

    @Override
    public ChannelSource getChannelSource(final Identifier identifier)
            throws IOException {
        Reference url = getUrl(identifier);
        logger.debug("Resolved {} to {}", identifier, url);
        try {
            // Issue an HTTP HEAD request to check whether the underlying
            // resource is accessible
            Client client = new Client(new Context(), url.getSchemeProtocol());
            ClientResource resource = new ClientResource(url);
            resource.setNext(client);
            resource.head();
            return new HttpChannelSource(url);
        } catch (ResourceException e) {
            if (e.getStatus().equals(Status.CLIENT_ERROR_NOT_FOUND) ||
                    e.getStatus().equals(Status.CLIENT_ERROR_GONE)) {
                throw new FileNotFoundException(e.getMessage());
            } else if (e.getStatus().equals(Status.CLIENT_ERROR_FORBIDDEN)) {
                throw new AccessDeniedException(e.getMessage());
            } else {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    @Override
    public SourceFormat getSourceFormat(final Identifier identifier)
            throws IOException {
        SourceFormat format = ResolverUtil.inferSourceFormat(identifier);
        if (format == SourceFormat.UNKNOWN) {
            format = getSourceFormatFromContentTypeHeader(identifier);
        }
        getChannelSource(identifier).newChannel(); // throws IOException if not found etc.
        return format;
    }

    public Reference getUrl(final Identifier identifier) throws IOException {
        final Configuration config = Application.getConfiguration();

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
     * Issues an HTTP HEAD request and checks the Content-Type header in the
     * response to determine the source format.
     *
     * @param identifier
     * @return A source format, or {@link SourceFormat#UNKNOWN} if unknown.
     * @throws IOException
     */
    private SourceFormat getSourceFormatFromContentTypeHeader(Identifier identifier)
            throws IOException {
        SourceFormat sourceFormat = SourceFormat.UNKNOWN;
        String contentType = "";
        Reference url = getUrl(identifier);
        try {
            Client client = new Client(new Context(), url.getSchemeProtocol());
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
                logger.warn("Failed to determine source format based on a " +
                        "Content-Type of {}", contentType);
            } else {
                logger.warn("Failed to determine source format (missing " +
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
     * @throws FileNotFoundException If the delegate script does not exist
     * @throws IOException
     * @throws ScriptException If the script fails to execute
     */
    private Reference getUrlWithScriptStrategy(Identifier identifier)
            throws IOException, ScriptException {
        final ScriptEngine engine = ScriptEngineFactory.getScriptEngine();
        final String[] args = { identifier.toString() };
        final String method = "Cantaloupe::get_url";
        final long msec = System.currentTimeMillis();
        final Object result = engine.invoke(method, args);
        logger.debug("{} load+exec time: {} msec", method,
                System.currentTimeMillis() - msec);
        if (result == null) {
            throw new FileNotFoundException(method + " returned nil for " +
                    identifier);
        }
        return new Reference((String) result);
    }

}
