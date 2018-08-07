import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.HashSet;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;

/**
 * This class provides a <code>shortestPath</code> method and <code>routeDirections</code> for
 * finding routes between two points on the map.
 */
public class Router {
    /**
     * Return a <code>List</code> of vertex IDs corresponding to the shortest path from a given
     * starting coordinate and destination coordinate.
     *
     * @param g       <code>GraphDB</code> data source.
     * @param stlon   The longitude of the starting coordinate.
     * @param stlat   The latitude of the starting coordinate.
     * @param destlon The longitude of the destination coordinate.
     * @param destlat The latitude of the destination coordinate.
     * @return The <code>List</code> of vertex IDs corresponding to the shortest path.
     */
    public static List<Long> shortestPath(GraphDB g,
                                          double stlon, double stlat,
                                          double destlon, double destlat) {
        HashMap<Long, Double> priorities = new HashMap<>();
        HashMap<Long, Double> distance = new HashMap<>();
        HashMap<Long, Long> nodeParent = new HashMap<>();
        PriorityQueue<Long> fringe = new PriorityQueue<>((a, b) -> Double.compare(priorities.get(a),
                priorities.get(b)));
        HashSet<Long> visited = new HashSet<>();

        long s = g.closest(stlon, stlat);
        long t = g.closest(destlon, destlat);

        distance.put(s, 0.0);
        nodeParent.put(s, s);
        double priority = g.distance(s, t);
        priorities.put(s, priority);

        fringe.add(s);
        shortPathHelp(priorities, distance, nodeParent, visited, fringe, g, t);

        List<Long> shortPath = new ArrayList<>();
        long temp = t;
        while (temp != s) {
            if (nodeParent.get(temp) == null) {
                break;
            }
            shortPath.add(0, temp);
            temp = nodeParent.get(temp);
        }
        shortPath.add(0, temp);
        return shortPath;
    }

    private static void shortPathHelp(HashMap<Long, Double> priorities, HashMap<Long, Double>
            distance, HashMap<Long, Long> nodeParent, HashSet<Long> visited,
                                      PriorityQueue<Long> fringe, GraphDB g, Long t) {

        while (!fringe.isEmpty()) {
            long vId = fringe.poll();
            if (vId == t) {
                return;
            }
            if (!visited.contains(vId)) {
                visited.add(vId);
                for (long w : g.adjacent(vId)) {
                    if (w != nodeParent.get(vId)) {
                        double vToW = g.distance(vId, w); //ed(v, w)
                        double sToW = distance.get(vId) + vToW; //d(s, v) + ed(v, w)
                        if (!distance.containsKey(w) || distance.get(w) > sToW) {
                            distance.put(w, sToW);
                            nodeParent.put(w, vId);

                            double priority = (sToW + g.distance(w, t));

                            priorities.put(w, priority);
                            fringe.add(w);
                        }

                    }
                }
            }
//            d(s, v): best known distance from s to v
//            ed(v, w): euclidean distance from v to w
//            h(w): euclidean distance from w to goal

//            If d(s, v) + ed(v, w) is less than d(s, w) in best:
//            update best so that d(s, w) = d(s, v) + ed(v, w)
//            add w to the fringe with a priority equal to d(s, v) + ed(v, w) + h(w).
        }
    }

    /**
     * Given a <code>route</code> of vertex IDs, return a <code>List</code> of
     * <code>NavigationDirection</code> objects representing the travel directions in order.
     *
     * @param g     <code>GraphDB</code> data source.
     * @param route The shortest-path route of vertex IDs.
     * @return A new <code>List</code> of <code>NavigationDirection</code> objects.
     */
    public static List<NavigationDirection> routeDirections(GraphDB g, List<Long> route) {
        return Collections.emptyList();
    }

    /**
     * Class to represent a navigation direction, which consists of 3 attributes:
     * a direction to go, a way, and the distance to travel for.
     */
    public static class NavigationDirection {

        /**
         * Integer constants representing directions.
         */
        public static final int START = 0, STRAIGHT = 1, SLIGHT_LEFT = 2, SLIGHT_RIGHT = 3,
                RIGHT = 4, LEFT = 5, SHARP_LEFT = 6, SHARP_RIGHT = 7;

        /**
         * Number of directions supported.
         */
        public static final int NUM_DIRECTIONS = 8;

        /**
         * A mapping of integer values to directions.
         */
        public static final String[] DIRECTIONS = new String[NUM_DIRECTIONS];

        static {
            DIRECTIONS[START] = "Start";
            DIRECTIONS[STRAIGHT] = "Go straight";
            DIRECTIONS[SLIGHT_LEFT] = "Slight left";
            DIRECTIONS[SLIGHT_RIGHT] = "Slight right";
            DIRECTIONS[RIGHT] = "Turn right";
            DIRECTIONS[LEFT] = "Turn left";
            DIRECTIONS[SHARP_LEFT] = "Sharp left";
            DIRECTIONS[SHARP_RIGHT] = "Sharp right";
        }

        /**
         * The direction represented.
         */
        int direction;
        /**
         * The name of this way.
         */
        String way;
        /**
         * The distance along this way.
         */
        double distance = 0.0;

        /**
         * Returns a new <code>NavigationDirection</code> from a string representation.
         *
         * @param dirAsString <code>String</code> instructions for a navigation direction.
         * @return A new <code>NavigationDirection</code> based on the string, or <code>null</code>
         * if unable to parse.
         */
        public static NavigationDirection fromString(String dirAsString) {
            String regex = "([a-zA-Z\\s]+) on ([\\w\\s]*) and continue for ([0-9\\.]+) miles\\.";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(dirAsString);
            NavigationDirection nd = new NavigationDirection();
            if (m.matches()) {
                String direction = m.group(1);
                if (direction.equals("Start")) {
                    nd.direction = NavigationDirection.START;
                } else if (direction.equals("Go straight")) {
                    nd.direction = NavigationDirection.STRAIGHT;
                } else if (direction.equals("Slight left")) {
                    nd.direction = NavigationDirection.SLIGHT_LEFT;
                } else if (direction.equals("Slight right")) {
                    nd.direction = NavigationDirection.SLIGHT_RIGHT;
                } else if (direction.equals("Turn right")) {
                    nd.direction = NavigationDirection.RIGHT;
                } else if (direction.equals("Turn left")) {
                    nd.direction = NavigationDirection.LEFT;
                } else if (direction.equals("Sharp left")) {
                    nd.direction = NavigationDirection.SHARP_LEFT;
                } else if (direction.equals("Sharp right")) {
                    nd.direction = NavigationDirection.SHARP_RIGHT;
                } else {
                    return null;
                }

                nd.way = m.group(2);
                try {
                    nd.distance = Double.parseDouble(m.group(3));
                } catch (NumberFormatException e) {
                    return null;
                }
                return nd;
            } else {
                // Not a valid nd
                return null;
            }
        }

        public String toString() {
            return String.format("%s on %s and continue for %.3f miles.",
                    DIRECTIONS[direction], way, distance);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof NavigationDirection) {
                return direction == ((NavigationDirection) o).direction
                        && way.equals(((NavigationDirection) o).way)
                        && distance == ((NavigationDirection) o).distance;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(direction, way, distance);
        }
    }
}
