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

    private static final double DEFAULT_THRESHOLD_DISTANCE = 100.0;

    @BeforeEach
    void setUp() {
        helper = new DirectionFilterHelper();
        geometryFactory = new GeometryFactory();
    }

    @Test
    void testFilterPathsByDirection_NullInput() {
        List<LineString> result = helper.filterPathsByDirection(null, true, DirectionFilterHelper.Direction.NORTH, DEFAULT_THRESHOLD_DISTANCE);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterPathsByDirection_EmptyList() {
        List<LineString> result = helper.filterPathsByDirection(Collections.emptyList(), true, DirectionFilterHelper.Direction.NORTH, DEFAULT_THRESHOLD_DISTANCE);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFilterPathsByDirection_SinglePath() {
        LineString line = createLineString(0, 0, 0, 1);
        List<LineString> paths = Collections.singletonList(line);

        List<LineString> result = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.NORTH, DEFAULT_THRESHOLD_DISTANCE);

        assertEquals(1, result.size());
        assertEquals(line, result.get(0));
    }

    @Test
    void testFilterPathsByDirection_BothDirection() {
        LineString northPath = createLineString(0, 0, 0, 2);
        LineString southPath = createLineString(0, 0, 0, -2);
        List<LineString> paths = Arrays.asList(northPath, southPath);

        List<LineString> result = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.BOTH, DEFAULT_THRESHOLD_DISTANCE);

        assertEquals(2, result.size());
    }

    @Test
    void testFilterPathsByDirection_UnknownDirection() {
        LineString northPath = createLineString(0, 0, 0, 2);
        LineString southPath = createLineString(0, 0, 0, -2);
        List<LineString> paths = Arrays.asList(northPath, southPath);

        List<LineString> result = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.UNKNOWN, DEFAULT_THRESHOLD_DISTANCE);

        assertEquals(2, result.size());
    }

    @ParameterizedTest
    @CsvSource({
        "NORTH, 0, 0, 0, 2, 0, 0, 0, -2, true",
        "SOUTH, 0, 2, 0, 0, 0, -2, 0, 0, true",
        "EAST, 0, 0, 2, 0, 0, 0, -2, 0, true",
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

        List<LineString> result = helper.filterPathsByDirection(paths, true, direction, DEFAULT_THRESHOLD_DISTANCE);

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

        List<LineString> result = helper.filterPathsByDirection(paths, false, direction, DEFAULT_THRESHOLD_DISTANCE);

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

        List<LineString> result = helper.filterPathsByDirection(paths, true, direction, DEFAULT_THRESHOLD_DISTANCE);

        assertSinglePathSelected(result, expectFirstPath ? path1 : path2);
    }

    @ParameterizedTest
    @CsvSource({
        "NORTH, 0, 0, 0, 0.0001, 0, 0, 0, -0.0001",
        "EAST, 0, 0, 0.0001, 0, 0, 0, -0.0001, 0"
    })
    void testFilterPathsByDirection_BelowThreshold_DefaultsToFirstPath(
            DirectionFilterHelper.Direction direction,
            double x1Path1, double y1Path1, double x2Path1, double y2Path1,
            double x1Path2, double y1Path2, double x2Path2, double y2Path2) {

        LineString path1 = createLineString(x1Path1, y1Path1, x2Path1, y2Path1);
        LineString path2 = createLineString(x1Path2, y1Path2, x2Path2, y2Path2);
        List<LineString> paths = Arrays.asList(path1, path2);

        List<LineString> result = helper.filterPathsByDirection(paths, true, direction, DEFAULT_THRESHOLD_DISTANCE);

        assertSinglePathSelected(result, path1);
    }

    @Test
    void testFilterPathsByDirection_Northeast_Downstream() {
        LineString path1 = createLineString(0, 0, 1, 1);
        LineString path2 = createLineString(0, 0, -1, -1);
        List<LineString> paths = Arrays.asList(path1, path2);

        List<LineString> result = helper.filterPathsByDirection(paths, false, DirectionFilterHelper.Direction.NORTHEAST, DEFAULT_THRESHOLD_DISTANCE);

        assertEquals(1, result.size());
        assertEquals(path2, result.get(0));
    }

    // Bug 17110 Regression Test: Road on the incorrect side of the road
    // as a result of the path section not going in the specified direction.
    // Test: Ensures that the correct side of the road is selected when filtering by direction.
    @Test
    void testFilterPathsByDirection_RegressionTest_Bug17110_RoadSectionNotInDirection() throws IOException {
        List<LineString> paths = loadLineStringsFromGeoJSON("test-examples/regression-direction-filter-test.json");

        List<LineString> resultPath = helper.filterPathsByDirection(paths, true, DirectionFilterHelper.Direction.WEST, 1609.0);
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
}
