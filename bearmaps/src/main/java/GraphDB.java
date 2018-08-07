import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Graph for storing all of the intersection (vertex) and road (edge) information.
 * Uses your GraphBuildingHandler to convert the XML files into a graph. Your
 * code must include the vertices, adjacent, distance, closest, lat, and lon
 * methods. You'll also need to include instance variables and methods for
 * modifying the graph (e.g. addNode and addEdge).
 *
 * @author Kevin Lowe, Antares Chen, Kevin Lin
 */
public class GraphDB {
    /**
     * Radius of the Earth in miles.
     */
    private static final int R = 3963;
    /**
     * Latitude centered on Berkeley.
     */
    private static final double ROOT_LAT = (MapServer.ROOT_ULLAT + MapServer.ROOT_LRLAT) / 2;
    /**
     * Longitude centered on Berkeley.
     */
    private static final double ROOT_LON = (MapServer.ROOT_ULLON + MapServer.ROOT_LRLON) / 2;
    /**
     * Scale factor at the natural origin, Berkeley. Prefer to use 1 instead of 0.9996 as in UTM.
     *
     * @source https://gis.stackexchange.com/a/7298
     */
    private static final double K0 = 1.0;
    /**
     * This constructor creates and starts an XML parser, cleans the nodes, and prepares the
     * data structures for processing. Modify this constructor to initialize your data structures.
     *
     * @param dbPath Path to the XML file to be parsed.
     */
    private HashMap<Long, Node> nodes = new HashMap<>();
    private HashMap<Long, HashMap<Long, Edge>> nodeEdges = new HashMap<>();
    private HashMap<Long, Nodeyyyy> nodeyyyys = new HashMap<>();
    private HashMap<Double, Long> findnodeyyyysX = new HashMap<>();
    private HashMap<Double, Long> findnodeyyyysY = new HashMap<>();
    private Nodeyyyy root;
    private double minX;
    private double minY;
    private double maxX;
    private double maxY;
    private Nodeyyyy currentClosestNode;

    public GraphDB(String dbPath) {
        File inputFile = new File(dbPath);
        try (FileInputStream inputStream = new FileInputStream(inputFile)) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(inputStream, new GraphBuildingHandler(this));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        clean();
        minX = 999999999;
        minY = 999999999;
        maxX = -999999999;
        minY = -999999999;
        root = buildKdTree(nodes.keySet(), 0, root);
    }

    /**
     * Helper to process strings into their "cleaned" form, ignoring punctuation and capitalization.
     *
     * @param s Input string.
     * @return Cleaned string.
     */
    private static String cleanString(String s) {
        return s.replaceAll("[^a-zA-Z ]", "").toLowerCase();
    }

    static double euclideanNodeMod(double x, double y, Nodeyyyy node) {
        if (node == null) {
            return 999999999;
        }
        return euclideanDistance(x, y, node.x, node.y);
    }

    static double euclideanDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    /**
     * Return the Euclidean x-value for some point, p, in Berkeley. Found by computing the
     * Transverse Mercator projection centered at Berkeley.
     *
     * @param lon The longitude for p.
     * @param lat The latitude for p.
     * @return The flattened, Euclidean x-value for p.
     * @source https://en.wikipedia.org/wiki/Transverse_Mercator_projection
     */
    static double projectToX(double lon, double lat) {
        double dlon = Math.toRadians(lon - ROOT_LON);
        double phi = Math.toRadians(lat);
        double b = Math.sin(dlon) * Math.cos(phi);
        return (K0 / 2) * Math.log((1 + b) / (1 - b));
    }

    /**
     * Return the Euclidean y-value for some point, p, in Berkeley. Found by computing the
     * Transverse Mercator projection centered at Berkeley.
     *
     * @param lon The longitude for p.
     * @param lat The latitude for p.
     * @return The flattened, Euclidean y-value for p.
     * @source https://en.wikipedia.org/wiki/Transverse_Mercator_projection
     */
    static double projectToY(double lon, double lat) {
        double dlon = Math.toRadians(lon - ROOT_LON);
        double phi = Math.toRadians(lat);
        double con = Math.atan(Math.tan(phi) / Math.cos(dlon));
        return K0 * (con - Math.toRadians(ROOT_LAT));
    }

    /**
     * Remove nodes with no connections from the graph.
     * While this does not guarantee that any two nodes in the remaining graph are connected,
     * we can reasonably assume this since typically roads are connected.
     */
    private void clean() {
        LinkedList<Long> toBeDeleted = new LinkedList<>();
        for (long i : nodes.keySet()) {
            if (nodeEdges.get(i) == null) {
                toBeDeleted.add(i);
            }
        }
        for (long i : toBeDeleted) {
            nodes.remove(i);
        }
    }

    /**
     * Returns the longitude of vertex <code>v</code>.
     *
     * @param v The ID of a vertex in the graph.
     * @return The longitude of that vertex, or 0.0 if the vertex is not in the graph.
     */
    double lon(long v) {
        if (!nodes.containsKey(v)) {
            return 0.0;
        }
        return nodes.get(v).lon;
    }

    /**
     * Returns the latitude of vertex <code>v</code>.
     *
     * @param v The ID of a vertex in the graph.
     * @return The latitude of that vertex, or 0.0 if the vertex is not in the graph.
     */
    double lat(long v) {
        if (!nodes.containsKey(v)) {
            return 0.0;
        }
        return nodes.get(v).lat;
    }

    /**
     * Returns an iterable of all vertex IDs in the graph.
     *
     * @return An iterable of all vertex IDs in the graph.
     */
    Iterable<Long> vertices() {
        return nodes.keySet();
    }

    /**
     * Returns an iterable over the IDs of all vertices adjacent to <code>v</code>.
     *
     * @param v The ID for any vertex in the graph.
     * @return An iterable over the IDs of all vertices adjacent to <code>v</code>, or an empty
     * iterable if the vertex is not in the graph.
     */
    Iterable<Long> adjacent(long v) {
        return nodes.get(v).edgyHashMap.keySet();
    }

    /**
     * Returns the great-circle distance between two vertices, v and w, in miles.
     * Assumes the lon/lat methods are implemented properly.
     *
     * @param v The ID for the first vertex.
     * @param w The ID for the second vertex.
     * @return The great-circle distance between vertices and w.
     * @source https://www.movable-type.co.uk/scripts/latlong.html
     */
    public double distance(long v, long w) {
        double phi1 = Math.toRadians(lat(v));
        double phi2 = Math.toRadians(lat(w));
        double dphi = Math.toRadians(lat(w) - lat(v));
        double dlambda = Math.toRadians(lon(w) - lon(v));

        double a = Math.sin(dphi / 2.0) * Math.sin(dphi / 2.0);
        a += Math.cos(phi1) * Math.cos(phi2) * Math.sin(dlambda / 2.0) * Math.sin(dlambda / 2.0);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * Returns the ID of the vertex closest to the given longitude and latitude.
     *
     * @param lon The given longitude.
     * @param lat The given latitude.
     * @return The ID for the vertex closest to the <code>lon</code> and <code>lat</code>.
     */
    public long closest(double lon, double lat) {

        double pointValueX = projectToX(lon, lat);
        double pointValueY = projectToY(lon, lat);
        if (pointValueX < minX) {
            minX = pointValueX;
        }
        if (pointValueX > maxX) {
            maxX = pointValueX;
        }
        if (pointValueY < minY) {
            minY = pointValueY;
        }
        if (pointValueY > maxY) {
            maxY = pointValueY;
        }
        Box boundingBox = new Box(minX, maxY, maxX, minY);
        currentClosestNode = null;
        closestHelper(root, pointValueX, pointValueY, boundingBox);
        return currentClosestNode.nodeId;
    }

    public void closestHelper(Nodeyyyy currNode, double x, double y, Box currBox) {
        if (currNode == null) {
            return;
        }
        if (euclideanNodeMod(x, y, currNode) < euclideanNodeMod(x, y, currentClosestNode)) {
            //found a shorter distance point (champion)
            currentClosestNode = currNode;
        }
        if (currNode.xOrY == 0) {
            Box leftBox = new Box(minX, maxY, currNode.x, minY);
            Box rightBox = new Box(currNode.x, maxY, maxX, minY);
            if (x <= currNode.x) {
                //in the left
                closestHelper(currNode.left, x, y, leftBox);
                if (euclideanNodeMod(x, y, currentClosestNode) < rightBox.pointToBox(x, y)) {
                    return;
                }
                closestHelper(currNode.right, x, y, rightBox);
            } else {
                //in the right
                closestHelper(currNode.right, x, y, rightBox);
                if (euclideanNodeMod(x, y, currentClosestNode) < leftBox.pointToBox(x, y)) {
                    return;
                }
                closestHelper(currNode.left, x, y, leftBox);
            }
        } else {
            Box bottomBox = new Box(minX, currNode.y, maxX, minY);
            Box topBox = new Box(minX, maxY, maxX, currNode.y);
            if (y <= currNode.y) {
                //in the bottom
                closestHelper(currNode.left, x, y, bottomBox);
                if (euclideanNodeMod(x, y, currentClosestNode) < topBox.pointToBox(x, y)) {
                    return;
                }
                closestHelper(currNode.right, x, y, topBox);
            } else {
                //in the top
                closestHelper(currNode.right, x, y, topBox);
                if (euclideanNodeMod(x, y, currentClosestNode) < bottomBox.pointToBox(x, y)) {
                    return;
                }
                closestHelper(currNode.left, x, y, bottomBox);
            }
        }

    }

    /**
     * In linear time, collect all the names of OSM locations that prefix-match the query string.
     *
     * @param prefix Prefix string to be searched for. Could be any case, with our without
     *               punctuation.
     * @return A <code>List</code> of the full names of locations whose cleaned name matches the
     * cleaned <code>prefix</code>.
     */
    public List<String> getLocationsByPrefix(String prefix) {
        return Collections.emptyList();
    }

    /**
     * Collect all locations that match a cleaned <code>locationName</code>, and return
     * information about each node that matches.
     *
     * @param locationName A full name of a location searched for.
     * @return A <code>List</code> of <code>LocationParams</code> whose cleaned name matches the
     * cleaned <code>locationName</code>
     */
    public List<LocationParams> getLocations(String locationName) {
        return Collections.emptyList();
    }

    void addNode(Node node) {
        nodes.put(node.id, node);
    }

    Node getNode(long id) {
        return nodes.get(id);
    }

    /**
     * Returns the initial bearing between vertices <code>v</code> and <code>w</code> in degrees.
     * The initial bearing is the angle that, if followed in a straight line along a great-circle
     * arc from the starting point, would take you to the end point.
     * Assumes the lon/lat methods are implemented properly.
     *
     * @param v The ID for the first vertex.
     * @param w The ID for the second vertex.
     * @return The bearing between <code>v</code> and <code>w</code> in degrees.
     * @source https://www.movable-type.co.uk/scripts/latlong.html
     */
    double bearing(long v, long w) {
        double phi1 = Math.toRadians(lat(v));
        double phi2 = Math.toRadians(lat(w));
        double lambda1 = Math.toRadians(lon(v));
        double lambda2 = Math.toRadians(lon(w));

        double y = Math.sin(lambda2 - lambda1) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2);
        x -= Math.sin(phi1) * Math.cos(phi2) * Math.cos(lambda2 - lambda1);
        return Math.toDegrees(Math.atan2(y, x));
    }

    public void addEdge(long v1, long v2) {
        Edge temp1 = new Edge(v1, v2);
        Edge temp2 = new Edge(v2, v1);
        //adding vertex data to vertices
        nodes.get(v1).edgyHashMap.put(v2, temp1);
        nodes.get(v2).edgyHashMap.put(v1, temp2);

        nodeEdges.put(v1, nodes.get(v1).edgyHashMap);
        nodeEdges.put(v2, nodes.get(v2).edgyHashMap);
    }

    Nodeyyyy buildKdTree(Set<Long> list, int depth, Nodeyyyy curr) {
        int n = list.size();
        if (n <= 0) {
            return null;
        }
        ArrayList<Double> sortedList = sortList(depth % 2, list);
        double median = sortedList.get(sortedList.size() / 2);

        if (depth % 2 == 0) {
            curr = new Nodeyyyy(findnodeyyyysX.get(median), 0);
            HashSet<Long> firstHalf = new HashSet<>();
            HashSet<Long> secondHalf = new HashSet<>();
            for (int i = 0; i < sortedList.size() / 2; i++) {
                firstHalf.add(findnodeyyyysX.get(sortedList.get(i)));
            }
            curr.left = buildKdTree(firstHalf, depth + 1, curr.left);
            for (int i = sortedList.size() / 2 + 1; i < sortedList.size(); i++) {
                secondHalf.add(findnodeyyyysX.get(sortedList.get(i)));
            }
            curr.right = buildKdTree(secondHalf, depth + 1, curr.right);
            return curr;

        } else {
            curr = new Nodeyyyy(findnodeyyyysY.get(median), 1);
            HashSet<Long> firstHalf = new HashSet<>();
            HashSet<Long> secondHalf = new HashSet<>();
            for (int i = 0; i < sortedList.size() / 2; i++) {
                firstHalf.add(findnodeyyyysY.get(sortedList.get(i)));
            }
            curr.left = buildKdTree(firstHalf, depth + 1, curr.left);
            for (int i = sortedList.size() / 2 + 1; i < sortedList.size(); i++) {
                secondHalf.add(findnodeyyyysY.get(sortedList.get(i)));
            }
            curr.right = buildKdTree(secondHalf, depth + 1, curr.right);
            return curr;
        }
    }


    public ArrayList sortList(int i, Set<Long> list) {
        ArrayList<Double> medianFinder = new ArrayList<>();
        if (i == 0) {
            for (long l : list) {
                Nodeyyyy n = new Nodeyyyy(l, i);
                if (n.x < minX) {
                    minX = n.x;
                }
                if (n.x > maxX) {
                    maxX = n.x;
                }
                medianFinder.add(n.x);
                nodeyyyys.put(l, n);
                findnodeyyyysX.put(n.x, l);
            }
            medianFinder.sort((a, b) -> Double.compare(a, b));
        } else {
            for (long l : list) {
                Nodeyyyy n = new Nodeyyyy(l, i);
                if (n.y < minY) {
                    minY = n.y;
                }
                if (n.y > maxY) {
                    maxY = n.y;
                }
                medianFinder.add(n.y);
                nodeyyyys.put(l, n);
                findnodeyyyysY.put(n.y, l);
            }
            medianFinder.sort((a, b) -> Double.compare(a, b));
        }
        return medianFinder;
    }

    //node class that put lat and lon info into a hash map
    static class Node {
        long id;
        double lat;
        double lon;

        HashMap<String, String> extraInfo;
        HashMap<Long, Edge> edgyHashMap;

        Node(long id, double lon, double lat) {
            this.id = id;
            this.lon = lon;
            this.lat = lat;
            this.extraInfo = new HashMap<>();
            this.edgyHashMap = new HashMap<>();
        }
    }

    //second node class to handle kd tree
    //verticalOrHorizontal: when 0 is x, 1 is y
    private class Nodeyyyy {
        long nodeId;
        double x;
        double y;
        int xOrY;
        private Nodeyyyy left;
        private Nodeyyyy right;

        Nodeyyyy(long nodeId, int xOrY) {
            this.nodeId = nodeId;
            this.xOrY = xOrY;
            x = projectToX(getNode(nodeId).lon, getNode(nodeId).lat);
            y = projectToY(getNode(nodeId).lon, getNode(nodeId).lat);
        }

        Nodeyyyy() {
        }
    }

    class Box {
        double topLeftX;
        double topLeftY;
        double bottomRightX;
        double bottomRightY;

        Box(double minX, double maxY, double maxX, double minY) {
            topLeftX = minX;
            topLeftY = maxY;
            bottomRightX = maxX;
            bottomRightY = minY;
        }

        public boolean containsNode(double x, double y) {
            if (x >= topLeftX && x <= bottomRightX
                    && y >= bottomRightY && y <= topLeftY) {
                return true;
            }
            return false;
        }

        public double pointToBox(double x, double y) {
            if (containsNode(x, y)) {
                return 0;
            } else if ((x >= topLeftX && x <= bottomRightX)) {
                return Math.min(Math.abs(y - topLeftY), Math.abs(y - bottomRightY));
            } else if ((y <= topLeftY && y >= bottomRightY)) {
                return Math.min(Math.abs(x - topLeftX), Math.abs(x - bottomRightX));
            } else {
                double lefttop = euclideanDistance(x, y, topLeftX, topLeftY);
                double righttop = euclideanDistance(x, y, bottomRightX, topLeftY);
                double leftbottom = euclideanDistance(x, y, topLeftX, bottomRightY);
                double rightbottom = euclideanDistance(x, y, bottomRightX, bottomRightY);
                return Math.min(lefttop, Math.min(righttop, Math.min(leftbottom, rightbottom)));
            }
        }
    }

    private class Edge {

        private long from;
        private long to;

        Edge(long from, long to) {
            this.from = from;
            this.to = to;
        }

        public String toString() {
            return "(" + from + ", " + to + ")";
        }

    }

}
