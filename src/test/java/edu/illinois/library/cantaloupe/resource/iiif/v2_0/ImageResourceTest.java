package edu.illinois.library.cantaloupe.resource.iiif.v2_0;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.ImageServerApplication;
import edu.illinois.library.cantaloupe.resource.ImageRepresentation;
import edu.illinois.library.cantaloupe.resource.ResourceTest;
import org.apache.commons.configuration.Configuration;
import org.restlet.data.CacheDirective;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Disposition;
import org.restlet.data.Header;
import org.restlet.data.Status;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageResourceTest extends ResourceTest {

    @Override
    protected ClientResource getClientForUriPath(String path) {
        return super.getClientForUriPath(ImageServerApplication.IIIF_2_0_PATH + path);
    }

    public void testBasicAuth() throws Exception {
        final String username = "user";
        final String secret = "secret";
        Application.stopServer();
        Configuration config = Application.getConfiguration();
        config.setProperty("http.auth.basic", "true");
        config.setProperty("http.auth.basic.username", username);
        config.setProperty("http.auth.basic.secret", secret);
        Application.startServer();

        // no credentials
        ClientResource client = getClientForUriPath("/jpg/full/full/0/default.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, client.getStatus());
        }

        // invalid credentials
        client.setChallengeResponse(
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, "invalid", "invalid"));
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_UNAUTHORIZED, client.getStatus());
        }

        // valid credentials
        client.setChallengeResponse(
                new ChallengeResponse(ChallengeScheme.HTTP_BASIC, username, secret));
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    public void testCacheHeadersWhenCachingEnabled() {
        Configuration config = Application.getConfiguration();
        config.setProperty("cache.client.enabled", "true");
        config.setProperty("cache.client.max_age", "1234");
        config.setProperty("cache.client.shared_max_age", "4567");
        config.setProperty("cache.client.public", "true");
        config.setProperty("cache.client.private", "false");
        config.setProperty("cache.client.no_cache", "false");
        config.setProperty("cache.client.no_store", "false");
        config.setProperty("cache.client.must_revalidate", "false");
        config.setProperty("cache.client.proxy_revalidate", "false");
        config.setProperty("cache.client.no_transform", "true");

        Map<String, String> expectedDirectives = new HashMap<>();
        expectedDirectives.put("max-age", "1234");
        expectedDirectives.put("s-maxage", "4567");
        expectedDirectives.put("public", null);
        expectedDirectives.put("no-transform", null);

        ClientResource client = getClientForUriPath("/jpg/full/full/0/default.jpg");
        client.get();
        List<CacheDirective> actualDirectives = client.getResponse().getCacheDirectives();
        for (CacheDirective d : actualDirectives) {
            if (d.getName() != null) {
                assertTrue(expectedDirectives.keySet().contains(d.getName()));
                if (d.getValue() != null) {
                    assertTrue(expectedDirectives.get(d.getName()).equals(d.getValue()));
                } else {
                    assertNull(expectedDirectives.get(d.getName()));
                }
            }
        }
    }

    public void testCacheHeadersWhenCachingDisabled() {
        Configuration config = Application.getConfiguration();
        config.setProperty("cache.client.enabled", "false");
        config.setProperty("cache.client.max_age", "1234");
        config.setProperty("cache.client.shared_max_age", "4567");
        config.setProperty("cache.client.public", "true");
        config.setProperty("cache.client.private", "false");
        config.setProperty("cache.client.no_cache", "false");
        config.setProperty("cache.client.no_store", "false");
        config.setProperty("cache.client.must_revalidate", "false");
        config.setProperty("cache.client.proxy_revalidate", "false");
        config.setProperty("cache.client.no_transform", "true");
        Application.setConfiguration(config);

        ClientResource client = getClientForUriPath("/jpg/full/full/0/default.jpg");
        client.get();
        assertEquals(0, client.getResponse().getCacheDirectives().size());
    }

    public void testContentDispositionHeader() {
        // no header
        ClientResource client = getClientForUriPath("/jpg/full/full/0/default.jpg");
        client.get();
        assertNull(client.getResponseEntity().getDisposition());

        // inline
        Configuration config = Application.getConfiguration();
        config.setProperty(ImageRepresentation.CONTENT_DISPOSITION_CONFIG_KEY,
                "inline");
        client.get();
        assertEquals(Disposition.TYPE_INLINE,
                client.getResponseEntity().getDisposition().getType());

        // attachment
        config.setProperty(ImageRepresentation.CONTENT_DISPOSITION_CONFIG_KEY,
                "attachment");
        client.get();
        assertEquals(Disposition.TYPE_ATTACHMENT,
                client.getResponseEntity().getDisposition().getType());
        assertEquals("jpg.jpg",
                client.getResponseEntity().getDisposition().getFilename());
    }

    /**
     * Tests that the Link header respects the <code>base_uri</code>
     * key in the configuration.
     */
    public void testLinkHeader() {
        Configuration config = Application.getConfiguration();
        ClientResource client = getClientForUriPath("/jpg/full/full/0/default.jpg");

        config.setProperty("base_uri", null);
        client.get();
        Header header = client.getResponse().getHeaders().getFirst("Link");
        assertTrue(header.getValue().startsWith("<http://localhost"));

        config.setProperty("base_uri", "https://example.org/");
        client.get();
        header = client.getResponse().getHeaders().getFirst("Link");
        System.out.println(header.getValue());
        assertTrue(header.getValue().startsWith("<https://example.org/"));
    }

    public void testNotFound() throws IOException {
        ClientResource client = getClientForUriPath("/invalid/info.json");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_NOT_FOUND, client.getStatus());
        }
    }

    public void testUnavailableSourceFormat() throws IOException {
        ClientResource client = getClientForUriPath("/text.txt/full/full/0/default.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,
                    client.getStatus());
        }
    }

    public void testUnavailableOutputFormat() throws IOException {
        ClientResource client = getClientForUriPath("/escher_logo.jpg/full/full/0/default.bogus");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }
    }

}