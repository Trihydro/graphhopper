package com.graphhopper.util;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Helper class for filtering paths based on cardinal and intercardinal directions.
 */
public class DirectionFilterHelper {

    private static final int BEARING_DUE_NORTHEAST = 45;
    private static final int BEARING_DUE_SOUTHEAST = 135;
    private static final int BEARING_DUE_SOUTHWEST = 225;
    private static final int BEARING_DUE_NORTHWEST = 315;

    public enum Direction {
        UNKNOWN,
        NORTH,
        SOUTH,
        EAST,
        WEST,
        NORTHWEST,
        NORTHEAST,
        SOUTHWEST,
        SOUTHEAST,
        BOTH
    }

    private static final EnumSet<Direction> CARDINAL_DIRECTIONS = EnumSet.of(
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    );

    /**
     * Filters a list of 2 LineStrings based on the specified cardinal or intercardinal direction.
     * Compares the furthest points of the first and last LineStrings to determine which aligns
     * best with the desired direction, returning only the selected LineString.
     * @param lineStrings       list of 2 LineStrings to filter
     * @param buildUpstream     whether the paths were built up or downstream
     * @param direction         the direction to filter by
     * @return A list of LineStrings filtered by the specified direction.
     */
    public List<LineString> filterPathsByDirection(List<LineString> lineStrings, Boolean buildUpstream, Direction direction) {
        if (lineStrings == null || lineStrings.isEmpty() || lineStrings.size() == 1) {
            return lineStrings != null ? lineStrings : Collections.emptyList();
        }

        if (direction == Direction.BOTH || direction == Direction.UNKNOWN) {
            return lineStrings;
        }

        Point furthestPointOfFirstPath = getTerminalPoint(lineStrings.get(0), buildUpstream);
        Point furthestPointOfSecondPath = getTerminalPoint(lineStrings.get(lineStrings.size() - 1), buildUpstream);

        boolean selectFirstPath = true;
        boolean isNonCardinal = !CARDINAL_DIRECTIONS.contains(direction);

        if (isNonCardinal) {
            // For non-cardinal directions: Calculate bearing between terminal points for better direction accuracy.
            // Since selectFirstPath defaults to true, we only need to adjust if the bearing is 'clearly the other direction.'
            // As in the cardinal directions, if the bearing is within 30 degrees of perpendicular to the desired direction,
            // then leave selectFirstPath = true.
            int bearing = (int) Math.round(AngleCalc.ANGLE_CALC.calcAzimuth(
                    furthestPointOfFirstPath.getY(), furthestPointOfFirstPath.getX(),
                    furthestPointOfSecondPath.getY(),furthestPointOfSecondPath.getX()));

            // compute target bearing for the requested intercardinal direction
            int targetBearing = switch (direction) {
                case NORTHEAST -> buildUpstream ? BEARING_DUE_NORTHEAST : BEARING_DUE_SOUTHWEST;
                case SOUTHEAST -> buildUpstream ? BEARING_DUE_SOUTHEAST : BEARING_DUE_NORTHWEST;
                case SOUTHWEST -> buildUpstream ? BEARING_DUE_SOUTHWEST : BEARING_DUE_NORTHEAST;
                case NORTHWEST -> buildUpstream ? BEARING_DUE_NORTHWEST : BEARING_DUE_SOUTHEAST;
                default -> 0;
            };

            // convert difference to range 0-180 (for instance, if two bearings are a difference of 190 then use 170 since going the other way is shorter)
            int diff = Math.abs(bearing - targetBearing);
            if (diff > 180) diff = 360 - diff;

            // more than 120 degrees off -> selectFirstPath = false
            selectFirstPath = diff <= 120;
        }
        else {
            // Calculate the total separation between the two terminal points
            double totalSeparation = Math.sqrt(
                    Math.pow(furthestPointOfFirstPath.getX() - furthestPointOfSecondPath.getX(), 2) +
                            Math.pow(furthestPointOfFirstPath.getY() - furthestPointOfSecondPath.getY(), 2)
            );
            double halfSeparation = totalSeparation / 2;

            switch (direction) {
                case NORTH:
                case SOUTH:
                    // Default to the first path if Y difference < half of total separation (paths too horizontal for N/S determination)
                    double yDiff = Math.abs(furthestPointOfFirstPath.getY() - furthestPointOfSecondPath.getY());
                    if (yDiff < halfSeparation) break;

                    selectFirstPath = compareCoordinates(
                        furthestPointOfFirstPath.getY(),
                        furthestPointOfSecondPath.getY(),
                        direction == Direction.NORTH,
                        buildUpstream
                    );
                    break;
                case EAST:
                case WEST:
                    // Default to the first path if X difference < half of total separation (paths too vertical for E/W determination)
                    double xDiff = Math.abs(furthestPointOfFirstPath.getX() - furthestPointOfSecondPath.getX());
                    if (xDiff < halfSeparation) break;

                    selectFirstPath = compareCoordinates(
                        furthestPointOfFirstPath.getX(),
                        furthestPointOfSecondPath.getX(),
                        direction == Direction.EAST,
                        buildUpstream
                    );
                    break;
                default:
                    break;
            }
        }

        return selectFirstPath
            ? Collections.singletonList(lineStrings.get(0))
            : Collections.singletonList(lineStrings.get(lineStrings.size() - 1));
    }

    /**
     * Retrieves the terminal point of a LineString based on the build direction.
     *
     * @param lineString the LineString to extract the terminal point from
     * @param buildUpstream if true, returns the start point; if false, returns the end point
     * @return the terminal Point of the LineString
     */
    private Point getTerminalPoint(LineString lineString, boolean buildUpstream) {
        return buildUpstream ? lineString.getStartPoint() : lineString.getEndPoint();
    }

    /**
     * Compares coordinate values to determine path selection based on the direction.
     *
     * @param firstCoordinate coordinate value from the first path (X for E/W, Y for N/S)
     * @param secondCoordinate coordinate value from the second path
     * @param isPositiveDirection true for NORTH/EAST, false for SOUTH/WEST
     * @param buildUpstream whether paths were built upstream
     * @return true to select the first path, false to select the second path
     */
    private boolean compareCoordinates(double firstCoordinate, double secondCoordinate, boolean isPositiveDirection, boolean buildUpstream) {
        if (isPositiveDirection) {
            return buildUpstream ? firstCoordinate < secondCoordinate : firstCoordinate > secondCoordinate;
        } else {
            return buildUpstream ? firstCoordinate > secondCoordinate : firstCoordinate < secondCoordinate;
        }
    }
}
