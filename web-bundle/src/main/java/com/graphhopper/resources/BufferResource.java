package com.graphhopper.resources;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphhopper.GraphHopper;
import com.graphhopper.GraphHopperConfig;
import com.graphhopper.buffer.BufferFeature;
import com.graphhopper.http.GHPointParam;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.Roundabout;
import com.graphhopper.routing.ev.VehicleAccess;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import com.graphhopper.util.shapes.GHPoint;
import com.graphhopper.util.shapes.GHPoint3D;

import org.checkerframework.checker.units.qual.g;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Pattern;

@Path("buffer")
public class BufferResource {
    private final LocationIndex locationIndex;
    private final NodeAccess nodeAccess;
    private final EdgeExplorer edgeExplorer;
    private final BooleanEncodedValue roundaboutAccessEnc;
    private final BooleanEncodedValue carAccessEnc;
    private final Graph graph;
    private final GraphHopperConfig config;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(1E8));

    // Section One: Change known abbreviations to be unabbreviated.
    // This is much more desirable than vice versa
    // because it ensures that misspellings of the unabbreviated are more likely
    // to get correctly matched. For instance, BOULEVARD and BOLEVARD will end up
    // correctly matched.
    private static final Pattern BOULEVARD = Pattern.compile("BLVD");
    private static final String BOULEVARD_REPLACEMENT = "BOULEVARD";
    private static final Pattern COURT = Pattern.compile(" CT");
    private static final String COURT_REPLACEMENT = " COURT";
    private static final Pattern STREET = Pattern.compile(" ST\\b");
    private static final String STREET_REPLACEMENT = "STREET";

    // Section Two: String manipulation of soundalikes WITHIN a word
    private static final Pattern KEEP_ONLY_ALPHANUMERIC_SPACE_OR_COMMA = Pattern.compile("[^A-Z0-9 ,]+");
    private static final String KEEP_ONLY_ALPHANUMERIC_SPACE_OR_COMMA_REPLACEMENT = "";
    private static final Pattern SCH = Pattern.compile("SCH");
    private static final String SCH_REPLACEMENT = "KK";
    private static final Pattern SC = Pattern.compile("SC");
    private static final String SC_REPLACEMENT = "K";
    private static final Pattern TIA = Pattern.compile("TIA");
    private static final String TIA_REPLACEMENT = "x"; // TIA needs to be before the 'D' is changed to 'T'
    private static final Pattern TIO = Pattern.compile("TIO");
    private static final String TIO_REPLACEMENT = "x";
    private static final Pattern TCH = Pattern.compile("TCH");
    private static final String TCH_REPLACEMENT = "x";
    private static final Pattern DG = Pattern.compile("DG");
    private static final String DG_REPLACEMENT = "j";
    private static final Pattern D = Pattern.compile("D");
    private static final String D_REPLACEMENT = "T";
    private static final Pattern MULTIPLE_R = Pattern.compile("RR+");
    private static final String MULTIPLE_R_REPLACEMENT = "R";
    private static final Pattern MULTIPLE_T = Pattern.compile("TT+");
    private static final String MULTIPLE_T_REPLACEMENT = "T";
    private static final Pattern MULTIPLE_P = Pattern.compile("PP+");
    private static final String MULTIPLE_P_REPLACEMENT = "P";
    private static final Pattern MULTIPLE_S = Pattern.compile("SS+");
    private static final String MULTIPLE_S_REPLACEMENT = "K";
    private static final Pattern MULTIPLE_F = Pattern.compile("FF+");
    private static final String MULTIPLE_F_REPLACEMENT = "F";
    private static final Pattern MULTIPLE_G = Pattern.compile("GG+");
    private static final String MULTIPLE_G_REPLACEMENT = "j";
    private static final Pattern MULTIPLE_L = Pattern.compile("LL+");
    private static final String MULTIPLE_L_REPLACEMENT = "L";
    private static final Pattern MULTIPLE_M = Pattern.compile("MM+");
    private static final String MULTIPLE_M_REPLACEMENT = "M";
    private static final Pattern MN = Pattern.compile("MN");
    private static final String MN_REPLACEMENT = "M";
    private static final Pattern MULTIPLE_N = Pattern.compile("NN+");
    private static final String MULTIPLE_N_REPLACEMENT = "N";
    private static final Pattern MULTIPLE_B = Pattern.compile("BB+");
    private static final String MULTIPLE_B_REPLACEMENT = "B";
    private static final Pattern MULTIPLE_V = Pattern.compile("VV+");
    private static final String MULTIPLE_V_REPLACEMENT = "V";
    private static final Pattern MULTIPLE_Z = Pattern.compile("ZZ+");
    private static final String MULTIPLE_Z_REPLACEMENT = "K";
    private static final Pattern FAKE_F = Pattern.compile("(?:GH|PH)");
    private static final String FAKE_F_REPLACEMENT = "F";
    private static final Pattern SILENT_INITIAL_CONSONANT_BEFORE_N = Pattern.compile("\\b[KGP]N");
    private static final String SILENT_INITIAL_CONSONANT_BEFORE_N_REPLACEMENT = "N";
    private static final Pattern MB = Pattern.compile("MB");
    private static final String MB_REPLACEMENT = "M";
    private static final Pattern CIA = Pattern.compile("CIA");
    private static final String CIA_REPLACEMENT = "x"; // ''CH' needs to be after 'SCH'
    private static final Pattern CH = Pattern.compile("CH");
    private static final String CH_REPLACEMENT = "x";
    private static final Pattern SIA = Pattern.compile("SIA");
    private static final String SIA_REPLACEMENT = "x";
    private static final Pattern SIO = Pattern.compile("SIO");
    private static final String SIO_REPLACEMENT = "x";
    private static final Pattern SH = Pattern.compile("SH");
    private static final String SH_REPLACEMENT = "x";
    private static final Pattern TH = Pattern.compile("TH");
    private static final String TH_REPLACEMENT = "c";
    private static final Pattern G = Pattern.compile("G");
    private static final String G_REPLACEMENT = "j";
    private static final Pattern CK = Pattern.compile("CK");
    private static final String CK_REPLACEMENT = "K";
    private static final Pattern Q = Pattern.compile("Q");
    private static final String Q_REPLACEMENT = "K";
    private static final Pattern S_C_Z = Pattern.compile("[SCZ]");
    private static final String S_C_Z_REPLACEMENT = "K";
    private static final Pattern X = Pattern.compile("X");
    private static final String X_REPLACEMENT = "KK";
    private static final Pattern AW_EW_OW = Pattern.compile("[AEO]W");
    private static final String AW_EW_OW_REPLACEMENT = "";

    // Section Three: Removing vowels and spaces should probably be the very last
    // thing that is done because many of the earlier regex operations are based on
    // how phonemes inside a word work and would give unexpected results across
    // words. For instance, expected result is to turn the 'GH' in 'ENOUGH' into an
    // 'F' but unexpected result would be to change the 'G H' in 'GOING HOME' into
    // an 'F'. Consider carefully before putting any regex after removing spaces.
    private static final Pattern REMOVE_ALL_VOWELS = Pattern.compile("[AEIOUY]");
    private static final String REMOVE_ALL_VOWELS_REPLACEMENT = "";
    private static final Pattern REMOVE_ALL_SPACES = Pattern.compile(" ");
    private static final String REMOVE_ALL_SPACES_REPLACEMENT = "";

    private static final Pattern[] SOUNDEX_PATTERNS = {
            BOULEVARD, COURT, STREET, KEEP_ONLY_ALPHANUMERIC_SPACE_OR_COMMA, SCH, SC, TIA, TIO, TCH, DG, D, MULTIPLE_R,
            MULTIPLE_T, MULTIPLE_P, MULTIPLE_S, MULTIPLE_F, MULTIPLE_G, MULTIPLE_L, MULTIPLE_M, MN, MULTIPLE_N,
            MULTIPLE_B, MULTIPLE_V, MULTIPLE_Z, FAKE_F, SILENT_INITIAL_CONSONANT_BEFORE_N, MB, CIA, CH, SIA, SIO, SH,
            TH, G, CK, Q, S_C_Z, X, AW_EW_OW, REMOVE_ALL_VOWELS, REMOVE_ALL_SPACES
    };
    private static final String[] SOUNDEX_REPLACEMENTS = {
            BOULEVARD_REPLACEMENT, COURT_REPLACEMENT, STREET_REPLACEMENT,
            KEEP_ONLY_ALPHANUMERIC_SPACE_OR_COMMA_REPLACEMENT, SCH_REPLACEMENT, SC_REPLACEMENT, TIA_REPLACEMENT,
            TIO_REPLACEMENT, TCH_REPLACEMENT, DG_REPLACEMENT, D_REPLACEMENT, MULTIPLE_R_REPLACEMENT,
            MULTIPLE_T_REPLACEMENT, MULTIPLE_P_REPLACEMENT, MULTIPLE_S_REPLACEMENT, MULTIPLE_F_REPLACEMENT,
            MULTIPLE_G_REPLACEMENT, MULTIPLE_L_REPLACEMENT, MULTIPLE_M_REPLACEMENT, MN_REPLACEMENT,
            MULTIPLE_N_REPLACEMENT, MULTIPLE_B_REPLACEMENT, MULTIPLE_V_REPLACEMENT, MULTIPLE_Z_REPLACEMENT,
            FAKE_F_REPLACEMENT, SILENT_INITIAL_CONSONANT_BEFORE_N_REPLACEMENT, MB_REPLACEMENT, CIA_REPLACEMENT,
            CH_REPLACEMENT, SIA_REPLACEMENT, SIO_REPLACEMENT, SH_REPLACEMENT, TH_REPLACEMENT, G_REPLACEMENT,
            CK_REPLACEMENT, Q_REPLACEMENT, S_C_Z_REPLACEMENT, X_REPLACEMENT, AW_EW_OW_REPLACEMENT,
            REMOVE_ALL_VOWELS_REPLACEMENT, REMOVE_ALL_SPACES_REPLACEMENT
    };

    @Inject
    public BufferResource(GraphHopperConfig config, GraphHopper graphHopper) {
        this.config = config;
        this.graph = graphHopper.getBaseGraph();
        this.locationIndex = graphHopper.getLocationIndex();
        this.nodeAccess = graph.getNodeAccess();
        this.edgeExplorer = graph.createEdgeExplorer();

        EncodingManager encodingManager = graphHopper.getEncodingManager();
        this.roundaboutAccessEnc = encodingManager.getBooleanEncodedValue(Roundabout.KEY);
        this.carAccessEnc = encodingManager.getBooleanEncodedValue(VehicleAccess.key("car"));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGet(
            @QueryParam("point") @NotNull GHPointParam point,
            @QueryParam("roadName") @NotNull String roadName,
            @QueryParam("thresholdDistance") @NotNull Double thresholdDistance,
            @QueryParam("queryMultiplier") @DefaultValue(".01") Double queryMultiplier,
            @QueryParam("buildUpstream") @DefaultValue("false") Boolean buildUpstream) {
        if (queryMultiplier > 1) {
            throw new IllegalArgumentException("Query multiplier is too high.");
        } else if (queryMultiplier <= 0) {
            throw new IllegalArgumentException("Query multiplier cannot be zero or negative.");
        }

        StopWatch sw = new StopWatch().start();

        roadName = sanitizeRoadNames(roadName).get(0);
        BufferFeature primaryStartFeature = calculatePrimaryStartFeature(point.get().lat, point.get().lon, roadName,
                queryMultiplier);
        EdgeIteratorState state = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);
        List<LineString> lineStrings = new ArrayList<>();

        // Start feature edge is bidirectional. Simple
        if (isBidirectional(state)) {
            lineStrings.add(computeBufferSegment(primaryStartFeature, roadName, thresholdDistance, buildUpstream, true));
            lineStrings.add(computeBufferSegment(primaryStartFeature, roadName, thresholdDistance, buildUpstream, false));
        }
        // Start feature edge is unidirectional. Requires finding sister road
        else {
            BufferFeature secondaryStartFeature = calculateSecondaryStartFeature(primaryStartFeature, roadName, .005);
            lineStrings.add(computeBufferSegment(primaryStartFeature, roadName, thresholdDistance, buildUpstream, buildUpstream));
            lineStrings.add(computeBufferSegment(secondaryStartFeature, roadName, thresholdDistance, buildUpstream, buildUpstream));
        }

        return createGeoJsonResponse(lineStrings, sw);
    }

    /**
     * Given a lat/long, finds all nearby edges with a corresponding road name. Uses
     * three expanding 'pulses' based off the queryMultiplier until at least a
     * single matching edge is found.
     *
     * @param startLat        latitude at center of query box
     * @param startLon        longitude at center of query box
     * @param roadName        name of road
     * @param queryMultiplier base dimension of query box
     * @return buffer feature closest to start lat/long
     */
    private BufferFeature calculatePrimaryStartFeature(Double startLat, Double startLon, String roadName,
                                                       Double queryMultiplier) {
        // Scale up query Bbox
        for (int i = 1; i < 4; i++) {
            BBox bbox = new BBox(startLon - queryMultiplier * i, startLon + queryMultiplier * i,
                    startLat - queryMultiplier * i, startLat + queryMultiplier * i);

            final List<Integer> filteredQueryEdges = queryBbox(bbox, roadName);

            if (!filteredQueryEdges.isEmpty()) {
                return computeStartFeature(filteredQueryEdges, startLat, startLon);
            }
        }

        throw new WebApplicationException("Could not find road with that name near the selection.");
    }

    /**
     * Given a buffer feature, finds all nearby edges with a corresponding road name which aren't along
     * the same road segment as the given feature. Uses three expanding 'pulses' based off the queryMultiplier
     * until at least a single matching edge is found.
     *
     * @param primaryStartFeature buffer feature to query off of
     * @param roadName            name of road
     * @param queryMultiplier     base dimension of query box
     * @return buffer feature closest to primary start feature
     */
    private BufferFeature calculateSecondaryStartFeature(BufferFeature primaryStartFeature, String roadName,
                                                         Double queryMultiplier) {
        double startLat = primaryStartFeature.getPoint().lat;
        double startLon = primaryStartFeature.getPoint().lon;

        EdgeIteratorState state = graph.getEdgeIteratorState(primaryStartFeature.getEdge(), Integer.MIN_VALUE);

        // Scale up query Bbox
        for (int i = 1; i < 4; i++) {
            BBox bbox = new BBox(startLon - queryMultiplier * i, startLon + queryMultiplier * i,
                    startLat - queryMultiplier * i, startLat + queryMultiplier * i);

            final List<Integer> filteredQueryEdges = queryBbox(bbox, roadName);
            final List<Integer> filteredEdgesByDirection = new ArrayList<>();

            // Secondary filter
            for (Integer edge : filteredQueryEdges) {
                EdgeIteratorState tempState = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

                // If both roads are going in different direction, target isn't bidirectional,
                // and target isn't the same edge as primary start feature
                if (tempState.getBaseNode() != state.getAdjNode() && tempState.getAdjNode() != state.getBaseNode()
                        && !isBidirectional(tempState) && !edge.equals(primaryStartFeature.getEdge())) {
                    filteredEdgesByDirection.add(edge);
                }
            }

            if (!filteredEdgesByDirection.isEmpty()) {
                return computeStartFeature(filteredEdgesByDirection, startLat, startLon);
            }
        }

        throw new WebApplicationException("Could not find road with that name near the selection.");
    }

    /**
     * Filters out all edges within a bbox that don't have a matching road name
     *
     * @param bbox     query zone
     * @param roadName name of road
     * @return all edges which have matching road name
     */
    private List<Integer> queryBbox(BBox bbox, String roadName) {
        final List<Integer> filteredEdgesInBbox = new ArrayList<>();

        this.locationIndex.query(bbox, edgeId -> {
            EdgeIteratorState state = graph.getEdgeIteratorState(edgeId, Integer.MIN_VALUE);
            List<String> queryRoadNames = getAllRouteNamesFromEdge(state);
            if (queryRoadNames.stream().anyMatch(x -> x.contains(roadName))) {
                filteredEdgesInBbox.add(edgeId);
            }
        });

        return filteredEdgesInBbox;
    }

    private String getSoundexRoadName(String incomingRoadName) {
        for (int i = 0; i < SOUNDEX_PATTERNS.length; i++) {
            incomingRoadName = SOUNDEX_PATTERNS[i].matcher(incomingRoadName).replaceAll(SOUNDEX_REPLACEMENTS[i]);
        }
        return incomingRoadName;
    }

    /**
     * Given a starting segment, finds the buffer feature at the distance threshold, the point along
     * that edge at the threshold, and the geometry of the starting edge.
     *
     * @param startFeature      buffer feature to start at
     * @param roadName          name of road
     * @param thresholdDistance maximum distance in meters
     * @param upstreamPath      direction to build path - either along or against
     *                          road's flow
     * @param upstreamStart     initial 'launch' direction - used only for a
     *                          bidirectional start
     * @return lineString representing start -> end
     */
    private LineString computeBufferSegment(BufferFeature startFeature, String roadName, Double thresholdDistance,
                                            Boolean upstreamPath, Boolean upstreamStart) {

        BufferFeature featureToThreshold = computeEdgeAtDistanceThreshold(startFeature, thresholdDistance, roadName,
                upstreamPath, upstreamStart);
        PointList finalSegmentToThreshold = computePointAtDistanceThreshold(startFeature, thresholdDistance,
                featureToThreshold, upstreamPath);
        PointList startingEdgeGeometry = computeWayGeometryOfStartingEdge(startFeature, upstreamStart, thresholdDistance);

        List<Coordinate> coordinates = new ArrayList<>();

        // Add start feature point
        for (GHPoint point : startingEdgeGeometry) {
            coordinates.add(new Coordinate(point.getLon(), point.getLat()));
        }

        // Add to threshold points
        for (GHPoint point : featureToThreshold.getPath()) {
            coordinates.add(new Coordinate(point.getLon(), point.getLat()));
        }

        // Add final segment points
        for (GHPoint point : finalSegmentToThreshold) {
            coordinates.add(new Coordinate(point.getLon(), point.getLat()));
        }

        // Reverse final path when building upstream
        if (upstreamPath) {
            Collections.reverse(coordinates);
        }

        // LineString must have at least 2 points
        if (coordinates.size() <= 1) {
            throw new WebApplicationException("Threshold distance is too short to construct a valid path.");
        }

        return geometryFactory.createLineString(coordinates.toArray(Coordinate[]::new));
    }

    /**
     * Combines the StreetName and StreetRef from an EdgeIteratorState. Each list can potentially
     * include different route names so we need to combine both lists.
     * I.e. streetNames contains "Purple Heart Trl" while streetRef contains "I 80"
     *
     * @param state the edge iterator state to fetch from
     * @return list of road names from street name and ref
     */
    private List<String> getAllRouteNamesFromEdge(EdgeIteratorState state) {
        List<String> streetNames = sanitizeRoadNames(state.getName());
        List<String> streetRef = sanitizeRoadNames((String) state.getValue("street_ref"));

        return Stream.concat(streetNames.stream(), streetRef.stream()).toList();
    }

    /**
     * Uses a strategy called Soundex https://en.wikipedia.org/wiki/Soundex to
     * convert each word into units of sound (sort of like syllables but more
     * technically 'phonemes'). This allows for many misspellings or typos to be
     * recognized as the intended word. For instance, Soundex can recognize that
     * someone typing BOULLEVARD likely meant BOULEVARD. After running a customized
     * Soundex, spaces are removed and the result is split by commas. The Soundex
     * strategy that we use compares words first by converting all words to
     * uppercase. This means that the comparisons are case-insensitive although the
     * regex results used during processing make use of casing to handle different
     * phonemes. For example, Soundex will find that HEDGES and hEDGes are identical
     * but it does this by comparing the Soundex cased result of HjS for both of
     * them. Note that the results are useful for comparison but are not fit for
     * return to User.
     *
     * @param roadNames comma-separated list of road names
     * @return list of split road names
     */
    private List<String> sanitizeRoadNames(String roadNames) {
        // Return empty list if roadNames is null
        if (roadNames == null || roadNames.trim().isEmpty()) {
            return new ArrayList<>();
        }

        roadNames = roadNames.toUpperCase();
        roadNames = getSoundexRoadName(roadNames);

        List<String> separatedNames = Arrays.asList(roadNames.split(","));
        return separatedNames;
    }

    /**
     * Cycles through all nearby, matching roads, and selects the point which is closest to the given lat/lon.
     *
     * @param edgeList all nearby edges with proper road name
     * @param startLat latitude at center of query box
     * @param startLon longitude at center of query box
     * @return closest point along road
     */
    private BufferFeature computeStartFeature(List<Integer> edgeList, Double startLat, Double startLon) {
        double lowestDistance = Double.MAX_VALUE;
        GHPoint3D nearestPoint = null;
        Integer nearestEdge = null;

        for (Integer edge : edgeList) {
            EdgeIteratorState state = graph.getEdgeIteratorState(edge, Integer.MIN_VALUE);

            PointList pointList = state.fetchWayGeometry(FetchMode.PILLAR_ONLY);

            for (GHPoint3D point : pointList) {
                double dist = DistancePlaneProjection.DIST_PLANE.calcDist(startLat, startLon, point.lat, point.lon);

                if (dist < lowestDistance) {
                    lowestDistance = dist;
                    nearestPoint = point;
                    nearestEdge = edge;
                }
            }
        }

        return new BufferFeature(nearestEdge, nearestPoint, 0.0);
    }

    /**
     * Iterates along the flow of a road given a starting feature until hitting the denoted threshold.
     * Usually only selects adjacent roads which have the matching road name, but certain road types like
     * roundabouts have separate logic.
     *
     * @param startFeature      buffer feature to start at
     * @param thresholdDistance maximum distance in meters
     * @param roadName          name of road
     * @param upstreamPath      direction to build path - either along or against
     *                          road's flow
     * @param upstreamStart     initial 'launch' direction - used only for a
     *                          bidirectional start
     * @return buffer feature at specified distance away from start
     */
    private BufferFeature computeEdgeAtDistanceThreshold(final BufferFeature startFeature, Double thresholdDistance,
                                                         String roadName, Boolean upstreamPath, Boolean upstreamStart) {
        List<Integer> usedEdges = new ArrayList<>() {
            {
                add(startFeature.getEdge());
            }
        };

        EdgeIteratorState currentState = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        int currentNode = upstreamStart ? currentState.getBaseNode() : currentState.getAdjNode();
        PointList path = new PointList();

        // Check starting edge
        double currentDistance = DistancePlaneProjection.DIST_PLANE.calcDist(startFeature.getPoint().getLat(),
                startFeature.getPoint().getLon(),
                nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode));
        double previousDistance = 0.0;
        Integer currentEdge = -1;

        if (currentDistance >= thresholdDistance) {
            return startFeature;
        }

        while (currentDistance < thresholdDistance) {
            EdgeIterator iterator = edgeExplorer.setBaseNode(currentNode);
            List<Integer> potentialEdges = new ArrayList<>();
            List<Integer> potentialRoundaboutEdges = new ArrayList<>();
            List<Integer> potentialRoundaboutEdgesWithoutName = new ArrayList<>();
            currentEdge = -1;

            while (iterator.next()) {
                List<String> roadNames = getAllRouteNamesFromEdge(iterator);
                Integer tempEdge = iterator.getEdge();
                EdgeIteratorState tempState = graph.getEdgeIteratorState(tempEdge, Integer.MIN_VALUE);

                // Temp hasn't been used before and has proper flow
                if (!usedEdges.contains(tempEdge) && hasProperFlow(currentState, tempState, upstreamPath)) {

                    // Temp has proper road name, isn't part of a roundabout, and bidirectional.
                    // Higher priority escape.
                    if (roadNames.stream().anyMatch(x -> x.contains(roadName))
                            && !tempState.get(this.roundaboutAccessEnc)
                            && isBidirectional(tempState)) {
                        currentEdge = tempEdge;
                        usedEdges.add(tempEdge);
                        break;
                    }

                    // Temp has proper road name and isn't part of a roundabout. Lower priority
                    // escape.
                    else if (roadNames.stream().anyMatch(x -> x.contains(roadName))
                            && !tempState.get(this.roundaboutAccessEnc)) {
                        potentialEdges.add(tempEdge);
                    }

                    // Temp has proper road name and is part of a roundabout. Higher entry priority.
                    else if (roadNames.stream().anyMatch(x -> x.contains(roadName))
                            && tempState.get(this.roundaboutAccessEnc)) {
                        potentialRoundaboutEdges.add(tempEdge);
                    }

                    // Temp is part of a roundabout. Lower entry priority.
                    else if (tempState.get(this.roundaboutAccessEnc)) {
                        potentialRoundaboutEdgesWithoutName.add(tempEdge);
                    }
                }
            }

            // No bidirectional edge found. Choose from potential edge lists.
            if (currentEdge == -1) {
                if (!potentialEdges.isEmpty()) {

                    // The Michigan Left
                    if (potentialEdges.size() > 1) {
                        // This logic is not infallible as it stands, but there's no clear alternative.
                        // In the case of a Michigan left, choose the edge that's further away

                        EdgeIteratorState tempState = graph.getEdgeIteratorState(potentialEdges.get(0),
                                Integer.MIN_VALUE);
                        double dist1 = Math.abs(
                                nodeAccess.getLat(tempState.getAdjNode()) - nodeAccess.getLat(tempState.getBaseNode()))
                                + Math.abs(nodeAccess.getLon(tempState.getAdjNode())
                                - nodeAccess.getLon(tempState.getBaseNode()));
                        tempState = graph.getEdgeIteratorState(potentialEdges.get(potentialEdges.size() - 1),
                                Integer.MIN_VALUE);
                        double dist2 = Math.abs(
                                nodeAccess.getLat(tempState.getAdjNode()) - nodeAccess.getLat(tempState.getBaseNode()))
                                + Math.abs(nodeAccess.getLon(tempState.getAdjNode())
                                - nodeAccess.getLon(tempState.getBaseNode()));

                        if (dist1 > dist2) {
                            currentEdge = potentialEdges.get(0);
                            usedEdges.add(currentEdge);
                        } else {
                            currentEdge = potentialEdges.get(potentialEdges.size() - 1);
                            usedEdges.add(currentEdge);
                        }
                    }
                    // Only one possible route
                    else {
                        currentEdge = potentialEdges.get(0);
                        usedEdges.add(currentEdge);
                    }
                } else if (!potentialRoundaboutEdges.isEmpty()) {
                    currentEdge = potentialRoundaboutEdges.get(0);
                    usedEdges.add(currentEdge);
                } else if (!potentialRoundaboutEdgesWithoutName.isEmpty()) {
                    currentEdge = potentialRoundaboutEdgesWithoutName.get(0);
                    usedEdges.add(currentEdge);
                } else {
                    throw new WebApplicationException("Dead end found.");
                }
            }

            // Calculate new distance
            int otherNode = graph.getOtherNode(currentEdge, currentNode);
            previousDistance = currentDistance;
            currentDistance += DistancePlaneProjection.DIST_PLANE.calcDist(nodeAccess.getLat(currentNode),
                    nodeAccess.getLon(currentNode), nodeAccess.getLat(otherNode), nodeAccess.getLon(otherNode));

            // Break before moving to next node in case hitting the threshold
            if (currentDistance >= thresholdDistance) {
                break;
            }

            EdgeIteratorState state = graph.getEdgeIteratorState(currentEdge, Integer.MIN_VALUE);
            PointList wayGeometry = state.fetchWayGeometry(FetchMode.PILLAR_ONLY);

            // Reverse path if segment is flipped
            if (state.getAdjNode() == currentNode) {
                if (!wayGeometry.isEmpty()) {
                    wayGeometry.reverse();
                }
            }
            // Add current node first
            path.add(new GHPoint(nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode)));
            path.add(wayGeometry);

            // Move to next node
            currentNode = otherNode;
            currentState = graph.getEdgeIteratorState(currentEdge, Integer.MIN_VALUE);
        }

        return new BufferFeature(currentEdge, currentNode,
                new GHPoint3D(nodeAccess.getLat(currentNode), nodeAccess.getLon(currentNode), 0),
                previousDistance, path);
    }

    /**
     * Iterates along the way geometry of a given edge until hitting the denoted threshold. Returns an empty list
     * when the edge of the start feature and end feature are the same.
     *
     * @param startFeature      original starting buffer feature
     * @param thresholdDistance maximum distance in meters
     * @param endFeature        buffer feature of edge at distance threshold
     * @param upstreamPath      direction to build path - either along or against road's flow
     * @return PointList to threshold along given edge of end feature
     */
    private PointList computePointAtDistanceThreshold(BufferFeature startFeature, Double thresholdDistance,
                                                      BufferFeature endFeature, Boolean upstreamPath) {
        EdgeIteratorState finalState = graph.getEdgeIteratorState(endFeature.getEdge(), Integer.MIN_VALUE);
        PointList pointList = finalState.fetchWayGeometry(FetchMode.PILLAR_ONLY);

        // It is possible that the finalState.fetchWayGeometry(FetchMode.xxxx) would
        // only contain TOWER points and not PILLAR points.
        // When this happens, filtering by FetchMode.PILLAR_ONLY will return an empty
        // PointList.
        if (pointList.size() == 0) {
            return new PointList();
        }

        // When the buffer is only as wide as a single edge, truncate one half of the
        // segment
        if (startFeature.getEdge().equals(endFeature.getEdge())) {
            return new PointList();
        }

        // Reverse geometry when starting at adjacent node
        if (upstreamPath && finalState.getAdjNode() == endFeature.getNode() || !upstreamPath && finalState.getAdjNode() == endFeature.getNode()) {
            pointList.reverse();
        }

        Double currentDistance = endFeature.getDistance();
        GHPoint3D previousPoint = pointList.get(0);

        return computePathListWithinThreshold(thresholdDistance, pointList, currentDistance, previousPoint);
    }

    /**
     * Truncates the geometry of the given start feature's edge based on the 'launch' direction then returns path.
     *
     * @param startFeature  original starting buffer feature
     * @param upstreamStart initial 'launch' direction
     * @return PointList of given start feature
     */
    private PointList computeWayGeometryOfStartingEdge(BufferFeature startFeature, Boolean upstreamStart, Double thresholdDistance) {
        EdgeIteratorState startState = graph.getEdgeIteratorState(startFeature.getEdge(), Integer.MIN_VALUE);
        PointList pathList = startState.fetchWayGeometry(FetchMode.ALL);
        PointList tempList = new PointList();

        // Truncate before startPoint
        if (upstreamStart) {
            for (GHPoint3D point : pathList) {
                tempList.add(point);
                if (startFeature.getPoint().equals(point)) {
                    break;
                }
            }
        }
        // Truncate after startPoint
        else {
            boolean pastPoint = false;
            for (GHPoint3D point : pathList) {
                if (startFeature.getPoint().equals(point)) {
                    pastPoint = true;
                }
                if (pastPoint) {
                    tempList.add(point);
                }
            }
        }

        // Doesn't matter if bidirectional since upstreamStart will always go toward adjacent node
        if (upstreamStart) {
            tempList.reverse();
        }

        double currentDistance = 0.0;
        GHPoint3D previousPoint = tempList.get(0);

        return computePathListWithinThreshold(thresholdDistance, tempList, currentDistance, previousPoint);
    }

    /**
     * Generates a list of points to create a path that does not exceed a threshold.
     *
     * @param thresholdDistance max distance to not exceed
     * @param origList          the original list to loop through during calculations
     * @param currentDistance   sum of the distance while moving through the original list
     * @param previousPoint     point to compute the distance from
     * @return a PointList containing a path not exceeding the threshold
     */
    private PointList computePathListWithinThreshold(Double thresholdDistance, PointList origList, double currentDistance, GHPoint3D previousPoint) {
        PointList pathList = new PointList();
        for (GHPoint3D currentPoint : origList) {
            // Filter zero-points made by PointList() scaling
            if (currentPoint.lat != 0 && currentPoint.lon != 0) {
                // Check if exceeds thresholdDistance
                currentDistance += DistancePlaneProjection.DIST_PLANE.calcDist(currentPoint.getLat(),
                        currentPoint.getLon(), previousPoint.getLat(), previousPoint.getLon());
                if (currentDistance >= thresholdDistance) {
                    return pathList;
                }

                pathList.add(currentPoint);
                previousPoint = currentPoint;
            }
        }
        // Default to full path in case the threshold isn't hit
        return pathList;
    }

    /**
     * Checks if the next edge is going the same direction as the current edge. Prioritizes bidirectional
     * roads to deal with a road where one-ways converge (e.g. -<)
     *
     * @param currentState current edge
     * @param tempState    edge to test
     * @param upstreamPath direction to build path - either along or against road's
     *                     flow
     * @return true if along the flow, false if roads converge
     */
    private Boolean hasProperFlow(EdgeIteratorState currentState, EdgeIteratorState tempState, Boolean upstreamPath) {
        // Going into a bidirectional road always has proper flow
        if (isBidirectional(tempState)) {
            return true;
        } else {
            // Coming from a bidirectional road means either its base or adjacent could
            // match
            if (isBidirectional(currentState)) {
                // For upstream, adjacent node must match
                if (upstreamPath) {
                    return tempState.getAdjNode() == currentState.getBaseNode()
                            || tempState.getAdjNode() == currentState.getAdjNode();
                }
                // For downstream, base node must match
                else {
                    return tempState.getBaseNode() == currentState.getBaseNode()
                            || tempState.getBaseNode() == currentState.getAdjNode();
                }
            }
            // Coming in from a unidirectional road means the opposite type node must match
            // the tempState's node
            else {
                // For upstream, adjacent node must match
                if (upstreamPath) {
                    return tempState.getAdjNode() == currentState.getBaseNode();
                }
                // For downstream, base node must match
                else {
                    return tempState.getBaseNode() == currentState.getAdjNode();
                }
            }
        }
    }

    /**
     * Checks if the road has access flags going in both directions.
     *
     * @param state edge under question
     * @return true if road is bidirectional
     */
    private Boolean isBidirectional(EdgeIteratorState state) {
        return state.get(this.carAccessEnc) && state.getReverse(this.carAccessEnc);
    }

    /**
     * Formats a list of lineStrings as a geoJson
     */
    private Response createGeoJsonResponse(List<LineString> lineStrings, StopWatch sw) {
        sw.stop();

        List<JsonFeature> features = new ArrayList<>();
        for (LineString lineString : lineStrings) {
            JsonFeature feature = new JsonFeature();
            feature.setGeometry(lineString);
            features.add(feature);
        }

        ObjectNode json = JsonNodeFactory.instance.objectNode();
        json.put("type", "FeatureCollection");
        json.putPOJO("copyrights", config.getCopyrights());
        json.putPOJO("features", features);
        return Response.ok(json).header("X-GH-Took", "" + sw.getSeconds() * 1000).build();
    }
}
