package edu.illinois.library.cantaloupe.resource.iiif.v1_1;

import edu.illinois.library.cantaloupe.Application;
import edu.illinois.library.cantaloupe.CantaloupeTestCase;
import edu.illinois.library.cantaloupe.ImageServerApplication;
import edu.illinois.library.cantaloupe.image.SourceFormat;
import edu.illinois.library.cantaloupe.processor.Processor;
import edu.illinois.library.cantaloupe.processor.ProcessorFactory;
import edu.illinois.library.cantaloupe.image.Identifier;
import edu.illinois.library.cantaloupe.image.OutputFormat;
import edu.illinois.library.cantaloupe.test.TestUtil;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.restlet.Client;
import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Functional test of conformance to the IIIF Image API 1.1 spec. Methods
 * are implemented in the order of the assertions in the spec document.</p>
 *
 * @see <a href="http://iiif.io/api/image/1.1/#image-info-request">IIIF Image
 * API 1.1</a>
 */
public class ConformanceTest extends CantaloupeTestCase {

    private static final Identifier IMAGE = new Identifier("escher_lego.jpg");
    private static final Integer PORT = TestUtil.getOpenPort();

    private static Client client = new Client(new Context(), Protocol.HTTP);

    public static BaseConfiguration newConfiguration() {
        BaseConfiguration config = new BaseConfiguration();
        try {
            File directory = new File(".");
            String cwd = directory.getCanonicalPath();
            Path fixturePath = Paths.get(cwd, "src", "test", "resources");
            config.setProperty("print_stack_trace_on_error_pages", false);
            config.setProperty("http.port", PORT);
            config.setProperty("processor.fallback", "Java2dProcessor");
            config.setProperty("resolver", "FilesystemResolver");
            config.setProperty("FilesystemResolver.lookup_strategy",
                    "BasicLookupStrategy");
            config.setProperty("FilesystemResolver.BasicLookupStrategy.path_prefix",
                    fixturePath + File.separator);
        } catch (Exception e) {
            fail("Failed to get the configuration");
        }
        return config;
    }

    private ClientResource getClientForUriPath(String path) {
        Reference url = new Reference(getBaseUri() + path);
        ClientResource resource = new ClientResource(url);
        resource.setNext(client);
        return resource;
    }

    private String getBaseUri() {
        return "http://localhost:" + PORT +
                ImageServerApplication.IIIF_1_1_PATH;
    }

    public void setUp() throws Exception {
        Application.setConfiguration(newConfiguration());
        Application.startServer();
    }

    public void tearDown() throws Exception {
        Application.stopServer();
    }

    /**
     * 2.2. "It is recommended that if the image’s base URI is dereferenced,
     * then the client should either redirect to the information request using
     * a 303 status code (see Section 6.1), or return the same result."
     *
     * @throws IOException
     */
    public void testBaseUriReturnsImageInfoViaHttp303() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE);
        client.setFollowingRedirects(false);
        client.get();
        assertEquals(Status.REDIRECTION_SEE_OTHER, client.getStatus());
        assertEquals(getBaseUri() + "/" + IMAGE + "/info.json",
                client.getLocationRef().toString());
    }

    /**
     * 3. "the identifier MUST be expressed as a string. All special characters
     * (e.g. ? or #) MUST be URI encoded to avoid unpredictable client
     * behaviors. The URL syntax relies upon slash (/) separators so any
     * slashes in the identifier MUST be URI encoded (aka. percent-encoded,
     * replace / with %2F )."
     *
     * @throws IOException
     */
    public void testIdentifierWithEncodedCharacters() throws IOException {
        // override the filesystem prefix to one folder level up so we can use
        // a slash in the identifier
        File directory = new File(".");
        String cwd = directory.getCanonicalPath();
        Path path = Paths.get(cwd, "src", "test");
        BaseConfiguration config = newConfiguration();
        config.setProperty("FilesystemResolver.BasicLookupStrategy.path_prefix",
                path + File.separator);
        Application.setConfiguration(config);

        String identifier = Reference.encode("resources/" + IMAGE);
        ClientResource client = getClientForUriPath("/" + identifier + "/info.json");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * 4.1
     *
     * @throws IOException
     */
    public void testFullRegion() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/native.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(594, image.getWidth());
        assertEquals(522, image.getHeight());
    }

    /**
     * 4.1
     *
     * @throws IOException
     */
    public void testAbsolutePixelRegion() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/20,20,100,100/full/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(100, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    /**
     * 4.1
     *
     * @throws IOException
     */
    public void testPercentageRegion() throws IOException {
        // with ints
        ClientResource client = getClientForUriPath("/" + IMAGE + "/pct:20,20,50,50/full/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(297, image.getWidth());
        assertEquals(261, image.getHeight());

        // with floats
        client = getClientForUriPath("/" + IMAGE + "/pct:20.2,20.6,50.2,50.6/full/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        rep = client.getResponseEntity();
        image = ImageIO.read(rep.getStream());
        assertEquals(298, image.getWidth());
        assertEquals(264, image.getHeight());
    }

    /**
     * 4.1. "If the request specifies a region which extends beyond the
     * dimensions of the source image, then the service should return an image
     * cropped at the boundary of the source image."
     *
     * @throws IOException
     */
    public void testAbsolutePixelRegionLargerThanSource() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/0,0,99999,99999/full/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(594, image.getWidth());
        assertEquals(522, image.getHeight());
    }

    /**
     * 4.1. "If the requested region's height or width is zero, or if the
     * region is entirely outside the bounds of the source image, then the
     * server MUST return a 400 (bad request) status code."
     *
     * @throws IOException
     */
    public void testPixelRegionOutOfBounds() throws IOException {
        // zero width/height
        ClientResource client = getClientForUriPath("/" + IMAGE + "/0,0,0,0/full/0/native.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }

        // We are not going to assert a 400 for "region entirely outside the
        // bounds of the reported dimensions" because it would require
        // ImageResource.doGet() to get the dimensions of the source image
        // (before any processing), which is unnecessarily expensive.
        /*
        // x/y out of bounds
        client = getClientForUriPath("/" + IMAGE + "/99999,99999,50,50/full/0/native.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }
        */
    }

    /**
     * 4.2
     *
     * @throws IOException
     */
    public void testFullSize() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(594, image.getWidth());
        assertEquals(522, image.getHeight());
    }

    /**
     * 4.2. "The extracted region should be scaled so that its width is
     * exactly equal to w, and the height will be a calculated value that
     * maintains the aspect ratio of the requested region."
     *
     * @throws IOException
     */
    public void testSizeScaledToFitWidth() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/50,/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(50, image.getWidth());
        assertEquals(43, image.getHeight());
    }

    /**
     * 4.2. "The extracted region should be scaled so that its height is
     * exactly equal to h, and the width will be a calculated value that
     * maintains the aspect ratio of the requested region."
     *
     * @throws IOException
     */
    public void testSizeScaledToFitHeight() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/,50/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(56, image.getWidth());
        assertEquals(50, image.getHeight());
    }

    /**
     * 4.2. "The width and height of the returned image is scaled to n% of the
     * width and height of the extracted region. The aspect ratio of the
     * returned image is the same as that of the extracted region."
     *
     * @throws IOException
     */
    public void testSizeScaledToPercent() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/pct:50/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(297, image.getWidth());
        assertEquals(261, image.getHeight());
    }

    /**
     * 4.2. "The width and height of the returned image are exactly w and h.
     * The aspect ratio of the returned image MAY be different than the
     * extracted region, resulting in a distorted image."
     *
     * @throws IOException
     */
    public void testAbsoluteWidthAndHeight() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/50,50/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());

        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(50, image.getWidth());
        assertEquals(50, image.getHeight());
    }

    /**
     * 4.2. "The image content is scaled for the best fit such that the
     * resulting width and height are less than or equal to the requested width
     * and height. The exact scaling MAY be determined by the service provider,
     * based on characteristics including image quality and system performance.
     * The dimensions of the returned image content are calculated to maintain
     * the aspect ratio of the extracted region."
     *
     * @throws IOException
     */
    public void testSizeScaledToFitInside() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/20,20/0/native.jpg");
        client.get();
        Representation rep = client.getResponseEntity();
        BufferedImage image = ImageIO.read(rep.getStream());
        assertEquals(20, image.getWidth());
        assertEquals(20, image.getHeight());
    }

    /**
     * 4.2. "If the resulting height or width is zero, then the server MUST
     * return a 400 (bad request) status code."
     *
     * @throws IOException
     */
    public void testResultingWidthOrHeightIsZero() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/pct:0/15/color.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }

        client = getClientForUriPath("/" + IMAGE + "/full/0,0/15/color.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }
    }

    /**
     * 4.3. "The rotation value represents the number of degrees of clockwise
     * rotation from the original, and may be any floating point number from 0
     * to 360. Initially most services will only support 0, 90, 180 or 270 as
     * valid values."
     *
     * @throws IOException
     */
    public void testRotation() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/15.5/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * IIIF Image API 1.1 doesn't say anything about an invalid rotation
     * parameter, so we will check for an HTTP 400.
     *
     * @throws IOException
     */
    public void testInvalidRotation() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/-15/native.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }

        client = getClientForUriPath("/" + IMAGE + "/full/full/385/native.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }
    }

    /**
     * 4.4. "The image is returned at an unspecified bit-depth."
     *
     * @throws IOException
     */
    public void testNativeQuality() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/native.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * 4.4. "The image is returned in full color, typically using 24 bits per
     * pixel."
     *
     * @throws IOException
     */
    public void testColorQuality() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/color.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * 4.4. "The image is returned in greyscale, where each pixel is black,
     * white or any degree of grey in between, typically using 8 bits per
     * pixel."
     *
     * @throws IOException
     */
    public void testGrayQuality() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/gray.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * 4.4. "The image returned is bitonal, where each pixel is either black or
     * white, using 1 bit per pixel when the format permits."
     *
     * @throws IOException
     */
    public void testBitonalQuality() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/bitonal.jpg");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * The IIIF Image API 1.1 doesn't say anything about unsupported qualities,
     * so we will check for an HTTP 400.
     *
     * @throws IOException
     */
    public void testUnsupportedQuality() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/bogus.jpg");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }
    }

    /**
     * 4.5
     *
     * @throws IOException
     */
    public void testFormats() throws Exception {
        testFormat(OutputFormat.JPG);
        testFormat(OutputFormat.TIF);
        testFormat(OutputFormat.PNG);
        testFormat(OutputFormat.GIF);
        testFormat(OutputFormat.JP2);
        testFormat(OutputFormat.PDF);
    }

    private void testFormat(OutputFormat format) throws Exception {
        ClientResource client = getClientForUriPath("/" + IMAGE +
                "/full/full/0/native." + format.getExtension());

        // does the current processor support this output format?
        SourceFormat sourceFormat = SourceFormat.getSourceFormat(IMAGE);
        Processor processor = ProcessorFactory.getProcessor(sourceFormat);
        if (processor.getAvailableOutputFormats(sourceFormat).contains(format)) {
            client.get();
            assertEquals(Status.SUCCESS_OK, client.getStatus());
            assertEquals(format.getMediaType(),
                    client.getResponse().getHeaders().getFirst("Content-Type").getValue());
        } else {
            try {
                client.get();
                fail("Expected exception");
            } catch (ResourceException e) {
                assertEquals(Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE, client.getStatus());
            }
        }
    }

    /**
     * 4.5
     *
     * @throws IOException
     */
    public void testUnsupportedFormat() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/native.bogus");
        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_BAD_REQUEST, client.getStatus());
        }
    }

    /**
     * 4.5 "If the format is not specified in the URI, then the server SHOULD
     * use the HTTP Accept header to determine the client’s preferences for the
     * format. The server may either do 200 (return the representation in the
     * response) or 30x (redirect to the correct URI with a format extension)
     * style content negotiation."
     */
    public void testFormatInAcceptHeader() {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/native");
        client.accept(MediaType.IMAGE_PNG);
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
        assertEquals(MediaType.IMAGE_PNG.toString(),
                client.getResponse().getHeaders().getFirst("Content-Type").getValue());
    }

    /**
     * 4.5 "If neither [format in URL or in Accept header] are given, then the
     * server should use a default format of its own choosing."
     */
    public void testNoFormat() {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/full/full/0/native");
        client.accept(MediaType.ALL);
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
        // TODO: this is kind of brittle
        List<String> mediaTypes = new ArrayList<>();
        mediaTypes.add("image/gif");
        mediaTypes.add("image/jpeg");
        mediaTypes.add("image/png");
        mediaTypes.add("image/tiff");
        assertTrue(mediaTypes.contains(
                client.getResponse().getHeaders().getFirst("Content-Type").getValue()));
    }

    /**
     * 5. "The service MUST return technical information about the requested
     * image in the JSON format."
     *
     * @throws IOException
     */
    public void testInformationRequest() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/info.json");
        client.get();
        assertEquals(Status.SUCCESS_OK, client.getStatus());
    }

    /**
     * 5. "The content-type of the response must be either “application/json”,
     * (regular JSON), or “application/ld+json” (JSON-LD)."
     *
     * @throws IOException
     */
    public void testInformationRequestContentType() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/info.json");
        client.get();
        assertEquals("application/json; charset=UTF-8",
                client.getResponse().getHeaders().getFirst("Content-Type").getValue());
    }

    /**
     * 5.
     *
     * @throws IOException
     */
    public void testInformationRequestJson() throws IOException {
        // this will be tested in InformationResourceTest
    }

    /**
     * 6.2 "Requests are limited to 1024 characters."
     *
     * @throws IOException
     */
    public void testUriTooLong() throws IOException {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/info.json");
        Reference uri = client.getReference();
        String uriStr = StringUtils.rightPad(uri.toString(), 1025, "a");
        client.setReference(new Reference(uriStr));

        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_REQUEST_URI_TOO_LONG,
                    client.getStatus());
        }

        client = getClientForUriPath("/" + IMAGE + "/full/full/0/native.jpg");
        uri = client.getReference();
        uriStr = StringUtils.rightPad(uri.toString(), 1025, "a");
        client.setReference(new Reference(uriStr));

        try {
            client.get();
            fail("Expected exception");
        } catch (ResourceException e) {
            assertEquals(Status.CLIENT_ERROR_REQUEST_URI_TOO_LONG,
                    client.getStatus());
        }
    }

    /**
     * 8. "A service should specify on all responses the extent to which the
     * API is supported. This is done by including an HTTP Link header
     * (RFC5988) entry pointing to the description of the highest level of
     * conformance of which ALL of the requirements are met."
     */
    public void testComplianceLevelLinkHeader() {
        ClientResource client = getClientForUriPath("/" + IMAGE + "/info.json");
        client.get();
        assertEquals("<http://library.stanford.edu/iiif/image-api/1.1/compliance.html#level2>;rel=\"profile\";",
                client.getResponse().getHeaders().getFirst("Link").getValue());

        client = getClientForUriPath("/" + IMAGE + "/full/full/0/native.jpg");
        client.get();
        assertEquals("<http://library.stanford.edu/iiif/image-api/1.1/compliance.html#level2>;rel=\"profile\";",
                client.getResponse().getHeaders().getFirst("Link").getValue());
    }

}
