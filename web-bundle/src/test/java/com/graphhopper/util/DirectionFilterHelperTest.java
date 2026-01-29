package com.graphhopper.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DirectionFilterHelperTest {

    private DirectionFilterHelper helper;
    private GeometryFactory geometryFactory;

    @BeforeEach
    void setUp() {
        helper = new DirectionFilterHelper();
        geometryFactory = new GeometryFactory();
    }

    @Test
    void testFilterPathsByDirection_NullInput() {
        List<LineString> result = helper.filterPathsByDirection(null, true, DirectionFilterHelper.Direction.NORTH);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterPathsByDirection_EmptyList() {
        List<LineString> result = helper.filterPathsByDirection(Collections.emptyList(), true, DirectionFilterHelper.Direction.NORTH);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterPathsByDirection_SinglePath() {
        LineString line = createLineString(0, 0, 0, 1);
        List<LineString> paths = Collections.singletonList(line);

        List<LineString> result = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.NORTH);

        assertEquals(1, result.size());
        assertEquals(line, result.get(0));
    }

    @Test
    void testFilterPathsByDirection_BothDirection() {
        LineString northPath = createLineString(0, 0, 0, 2);
        LineString southPath = createLineString(0, 0, 0, -2);
        List<LineString> paths = Arrays.asList(northPath, southPath);

        List<LineString> result = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.BOTH);

        assertEquals(2, result.size());
    }

    @Test
    void testFilterPathsByDirection_UnknownDirection() {
        LineString northPath = createLineString(0, 0, 0, 2);
        LineString southPath = createLineString(0, 0, 0, -2);
        List<LineString> paths = Arrays.asList(northPath, southPath);

        List<LineString> result = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.UNKNOWN);

        assertEquals(2, result.size());
    }

    @ParameterizedTest
    @CsvSource({
            "NORTH, 0, -2, 0, 0, 0, 2, 0, 0, true",
            "SOUTH, 0, 2, 0, 0, 0, -2, 0, 0, true",
            "EAST, -2, 0, 0, 0, 2, 0, 0, 0, true",
            "WEST, 2, 0, 0, 0, -2, 0, 0, 0, true"
    })
    void testFilterPathsByDirection_CardinalDirections_Upstream(
            DirectionFilterHelper.Direction direction,
            double x1Path1, double y1Path1, double x2Path1, double y2Path1,
            double x1Path2, double y1Path2, double x2Path2, double y2Path2,
            boolean expectFirstPath) {

        LineString path1 = createLineString(x1Path1, y1Path1, x2Path1, y2Path1);
        LineString path2 = createLineString(x1Path2, y1Path2, x2Path2, y2Path2);
        List<LineString> paths = Arrays.asList(path1, path2);

        List<LineString> result = helper.filterPathsByDirection(paths, true, direction);

        assertSinglePathSelected(result, expectFirstPath ? path1 : path2);
    }

    @ParameterizedTest
    @CsvSource({
            "NORTH, 0, 0, 0, 2, 0, 0, 0, -2, true",
            "SOUTH, 0, 0, 0, 2, 0, 0, 0, -2, false",
            "EAST, 0, 0, 2, 0, 0, 0, -2, 0, true",
            "WEST, 0, 0, 2, 0, 0, 0, -2, 0, false"
    })
    void testFilterPathsByDirection_CardinalDirections_Downstream(
            DirectionFilterHelper.Direction direction,
            double x1Path1, double y1Path1, double x2Path1, double y2Path1,
            double x1Path2, double y1Path2, double x2Path2, double y2Path2,
            boolean expectFirstPath) {

        LineString path1 = createLineString(x1Path1, y1Path1, x2Path1, y2Path1);
        LineString path2 = createLineString(x1Path2, y1Path2, x2Path2, y2Path2);
        List<LineString> paths = Arrays.asList(path1, path2);

        List<LineString> result = helper.filterPathsByDirection(paths, false, direction);

        assertSinglePathSelected(result, expectFirstPath ? path1 : path2);
    }

    @ParameterizedTest
    @CsvSource({
            "NORTHEAST, 0, 0, 1, 1, 0, 0, -1, -1, true",
            "SOUTHWEST, 0, 0, 1, 1, 0, 0, -1, -1, false",
            "NORTHWEST, 0, 0, -1, 1, -1, 1, -2, 2, true",
            "SOUTHEAST, 0, 0, 1, -1, 1, -1, 2, -2, true"
    })
    void testFilterPathsByDirection_IntercardinalDirections_Upstream(
            DirectionFilterHelper.Direction direction,
            double x1Path1, double y1Path1, double x2Path1, double y2Path1,
            double x1Path2, double y1Path2, double x2Path2, double y2Path2,
            boolean expectFirstPath) {

        LineString path1 = createLineString(x1Path1, y1Path1, x2Path1, y2Path1);
        LineString path2 = createLineString(x1Path2, y1Path2, x2Path2, y2Path2);
        List<LineString> paths = Arrays.asList(path1, path2);

        List<LineString> result = helper.filterPathsByDirection(paths, true, direction);

        assertSinglePathSelected(result, expectFirstPath ? path1 : path2);
    }

    @ParameterizedTest
    @CsvSource({
            "EAST, 0, 0.0001, 0, 0, 0, -0.0001, 0, 0",
            "NORTH, 0.0001, 0, 0, 0, -0.0001, 0, 0, 0"
    })
    void testFilterPathsByDirection_BelowThreshold_DefaultsToFirstPath(
            DirectionFilterHelper.Direction direction,
            double x1Path1, double y1Path1, double x2Path1, double y2Path1,
            double x1Path2, double y1Path2, double x2Path2, double y2Path2) {

        LineString path1 = createLineString(x1Path1, y1Path1, x2Path1, y2Path1);
        LineString path2 = createLineString(x1Path2, y1Path2, x2Path2, y2Path2);
        List<LineString> paths = Arrays.asList(path1, path2);

        List<LineString> result = helper.filterPathsByDirection(paths, true, direction);

        assertSinglePathSelected(result, path1);
    }

    @Test
    void testFilterPathsByDirection_ComprehensiveDirectionTest() {
        DirectionFilterHelper.Direction[] cardinals = {
                DirectionFilterHelper.Direction.NORTH,
                DirectionFilterHelper.Direction.EAST,
                DirectionFilterHelper.Direction.SOUTH,
                DirectionFilterHelper.Direction.WEST
        };
        DirectionFilterHelper.Direction[] intercardinals = {
                DirectionFilterHelper.Direction.NORTHEAST,
                DirectionFilterHelper.Direction.SOUTHEAST,
                DirectionFilterHelper.Direction.SOUTHWEST,
                DirectionFilterHelper.Direction.NORTHWEST
        };

        System.out.println("Starting comprehensive direction filtering test...");

        // Test with buildUpstream = true
        System.out.println("\n=== Testing with buildUpstream = true ===");
        runDirectionTest(cardinals, intercardinals, true);

        // Test with buildUpstream = false
        System.out.println("\n=== Testing with buildUpstream = false ===");
        runDirectionTest(cardinals, intercardinals, false);

        System.out.println("\nComprehensive direction filtering test completed.");
    }

    // Bug 17110 Regression Test: Road on the incorrect side of the road
    // as a result of the path section not going in the specified direction.
    // Test: Ensures that the correct side of the road is selected when filtering by direction.
    @Test
    void testFilterPathsByDirection_RegressionTest_Bug17110_RoadSectionNotInDirection() throws IOException {
        List<LineString> paths = loadLineStringsFromGeoJSON("test-examples/regression-direction-filter-test.json");

        List<LineString> resultPath = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.WEST);
        assertSinglePathSelected(resultPath, paths.get(0));
    }

    // Helper methods

    /**
     * Asserts that exactly one path was selected and it matches the expected path
     */
    private void assertSinglePathSelected(List<LineString> result, LineString expectedPath) {
        assertEquals(1, result.size(), "Expected exactly one path to be selected");
        assertEquals(expectedPath, result.get(0), "Expected specific path to be selected");
    }

    /**
     * Creates a simple two-point LineString from start to end coordinates
     */
    private LineString createLineString(double x1, double y1, double x2, double y2) {
        Coordinate[] coords = new Coordinate[]{
                new Coordinate(x1, y1),
                new Coordinate(x2, y2)
        };
        return geometryFactory.createLineString(coords);
    }

    /**
     * Loads LineString geometries from a GeoJSON FeatureCollection file
     * @param fileName name of the GeoJSON file in test resources
     * @return list of LineStrings from the file, or empty list if the file not found
     */
    private List<LineString> loadLineStringsFromGeoJSON(String fileName) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<LineString> lineStrings = new ArrayList<>();

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            assertNotNull(is, "Test resource not found: " + fileName);

            JsonNode root = mapper.readTree(is);
            JsonNode features = root.get("features");

            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode geometry = feature.get("geometry");
                    if (geometry != null && "LineString".equals(geometry.get("type").asText())) {
                        JsonNode coordinates = geometry.get("coordinates");
                        Coordinate[] coords = new Coordinate[coordinates.size()];

                        for (int i = 0; i < coordinates.size(); i++) {
                            JsonNode coord = coordinates.get(i);
                            coords[i] = new Coordinate(coord.get(0).asDouble(), coord.get(1).asDouble());
                        }

                        lineStrings.add(geometryFactory.createLineString(coords));
                    }
                }
            }
        }

        return lineStrings;
    }

    private void runDirectionTest(DirectionFilterHelper.Direction[] cardinals,
                                  DirectionFilterHelper.Direction[] intercardinals,
                                  boolean buildUpstream) {

        int unexpectedResults = 0;
        int totalTests = 0;

        // Iterate through first 6 test coordinate arrays only
        for (int arrayIndex = 0; arrayIndex < Math.min(6000, DirectionFilterTestData.getTestCoordinateArrayCount()); arrayIndex++) {
            double[][] coords = DirectionFilterTestData.getTestCoordinateArray(arrayIndex);

            // Extract points A, B, C, D, F
            Coordinate A = new Coordinate(coords[0][0], coords[0][1]);
            Coordinate B = new Coordinate(coords[1][0], coords[1][1]);
            Coordinate C = new Coordinate(coords[2][0], coords[2][1]);
            Coordinate D = new Coordinate(coords[3][0], coords[3][1]);
            Coordinate F = new Coordinate(coords[4][0], coords[4][1]);

            // Cycle through direction indexes
            for (int directionIndex = 0; directionIndex < cardinals.length; directionIndex++) {
                DirectionFilterHelper.Direction cardinal = cardinals[directionIndex];
                DirectionFilterHelper.Direction intercardinal = intercardinals[directionIndex];

                // Create LineStrings based on buildUpstream parameter
                LineString line1, line2, line3, line4;
                if (buildUpstream) {
                    line1 = createLineStringFromCoords(A, B); // AB
                    line2 = createLineStringFromCoords(C, B); // CB
                    line3 = createLineStringFromCoords(D, B); // DB
                    line4 = createLineStringFromCoords(F, B); // FB
                } else {
                    line1 = createLineStringFromCoords(B, A); // BA
                    line2 = createLineStringFromCoords(B, C); // BC
                    line3 = createLineStringFromCoords(B, D); // BD
                    line4 = createLineStringFromCoords(B, F); // BF
                }

                // First call: filterPathsByDirection with cardinal direction
                List<LineString> cardinalPaths = Arrays.asList(line1, line2);
                List<LineString> cardinalResult = helper.filterPathsByDirection(
                        cardinalPaths, buildUpstream, cardinal);

                // Second call: filterPathsByDirection with intercardinal direction
                List<LineString> intercardinalPaths = Arrays.asList(line3, line4);
                List<LineString> intercardinalResult = helper.filterPathsByDirection(
                        intercardinalPaths, buildUpstream, intercardinal);

                totalTests++;

                // Check if results meet expected criteria
                boolean isExpected = false;
                String expectedPattern = "";

                if (cardinalResult.size() == 1 && intercardinalResult.size() == 1) {
                    LineString cardinalSelected = cardinalResult.get(0);
                    LineString intercardinalSelected = intercardinalResult.get(0);

                    // Check for expected patterns
                    if ((cardinalSelected.equals(line1) && intercardinalSelected.equals(line3)) ||
                            (cardinalSelected.equals(line2) && intercardinalSelected.equals(line4))) {
                        isExpected = true;
                        expectedPattern = cardinalSelected.equals(line1) ? "Pattern 1 (AB->DB)" : "Pattern 2 (CB->FB)";
                        if (!buildUpstream) {
                            expectedPattern = cardinalSelected.equals(line1) ? "Pattern 1 (BA->BD)" : "Pattern 2 (BC->BF)";
                        }
                    }
                }

                if (!isExpected) {
                    unexpectedResults++;
                    System.out.printf(
                            "UNEXPECTED RESULT - Array %d, Direction Index %d (%s/%s), buildUpstream=%b:%n",
                            arrayIndex, directionIndex, cardinal, intercardinal, buildUpstream);
                    System.out.printf("  Points: A=%.2f,%.2f B=%.2f,%.2f C=%.2f,%.2f D=%.2f,%.2f F=%.2f,%.2f%n",
                            A.x, A.y, B.x, B.y, C.x, C.y, D.x, D.y, F.x, F.y);
                    System.out.printf("  Cardinal (%s) selected %d paths: %s%n",
                            cardinal, cardinalResult.size(),
                            cardinalResult.isEmpty() ? "none" :
                                    (cardinalResult.get(0).equals(line1) ? (buildUpstream ? "AB" : "BA") : (buildUpstream ? "CB" : "BC")));
                    System.out.printf("  Intercardinal (%s) selected %d paths: %s%n",
                            intercardinal, intercardinalResult.size(),
                            intercardinalResult.isEmpty() ? "none" :
                                    (intercardinalResult.get(0).equals(line3) ? (buildUpstream ? "DB" : "BD") : (buildUpstream ? "FB" : "BF")));
                } else {
                    System.out.printf(
                            "Expected result - Array %d, Direction Index %d: %s%n",
                            arrayIndex, directionIndex, expectedPattern);
                }
            }
        }

        System.out.printf(
                "\nTest Summary (buildUpstream=%b): %d/%d tests met expected criteria (%d unexpected)%n",
                buildUpstream, totalTests - unexpectedResults, totalTests, unexpectedResults);
    }

    /**
     * Creates a LineString from two Coordinate objects
     */
    private LineString createLineStringFromCoords(Coordinate start, Coordinate end) {
        Coordinate[] coords = new Coordinate[]{start, end};
        return geometryFactory.createLineString(coords);
    }
}
