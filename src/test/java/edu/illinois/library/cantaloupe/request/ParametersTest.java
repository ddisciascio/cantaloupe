package edu.illinois.library.cantaloupe.request;

import junit.framework.TestCase;

public class ParametersTest extends TestCase {

    private Parameters parameters;

    public void setUp() {
        parameters = new Parameters("identifier", "full", "full", "0",
                "default", "jpg");
    }

    public void testFromUri() {
        parameters = Parameters.fromUri("bla/20,20,50,50/pct:90/15/bitonal.jpg");
        assertEquals("bla", parameters.getIdentifier().toString());
        assertEquals("20,20,50,50", parameters.getRegion().toString());
        assertEquals(90f, parameters.getSize().getPercent());
        assertEquals(15f, parameters.getRotation().getDegrees());
        assertEquals(Quality.BITONAL, parameters.getQuality());
        assertEquals(OutputFormat.JPG, parameters.getOutputFormat());

        try {
            Parameters.fromUri("bla/20,20,50,50/15/bitonal.jpg");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            // pass
        }
        try {
            Parameters.fromUri("bla/20,20,50,50/pct:90/15/bitonal");
            fail("Expected exception");
        } catch (IllegalArgumentException e) {
            // pass
        }
    }

    public void testIsUnmodified() {
        parameters = new Parameters("identifier", "full", "full", "0",
                "default", "jpg");
        assertFalse(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.jpg", "full", "full", "0",
                "default", "jpg");
        assertTrue(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "full", "full", "0",
                "default", "gif");
        assertTrue(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "30,30,30,30", "full", "0",
                "default", "gif");
        assertFalse(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "full", "pct:100", "0",
                "default", "gif");
        assertTrue(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "full", "pct:50", "0",
                "default", "gif");
        assertFalse(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "full", "full", "2",
                "default", "gif");
        assertFalse(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "full", "full", "!0",
                "default", "gif");
        assertTrue(parameters.isRequestingUnmodifiedSource());

        parameters = new Parameters("identifier.gif", "full", "full", "0",
                "color", "gif");
        assertTrue(parameters.isRequestingUnmodifiedSource());
    }

    public void testToString() {
        String expected = "identifier/full/full/0/default.jpg";
        assertEquals(expected, parameters.toString());
    }

}
