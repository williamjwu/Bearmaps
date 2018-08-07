//import javafx.scene.layout.CornerRadii;

/**
 * This class provides all code necessary to take a query box and produce
 * a query result. The getMapRaster method must return a Map containing all
 * seven of the required fields, otherwise the front end code will probably
 * not draw the output correctly.
 */
public class Rasterer {
    /** The max image depth level. */
    public static final int MAX_DEPTH = 7;

    /**
     * Takes a user query and finds the grid of images that best matches the query. These images
     * will be combined into one big image (rastered) by the front end. The grid of images must obey
     * the following properties, where image in the grid is referred to as a "tile".
     * <ul>
     *     <li>The tiles collected must cover the most longitudinal distance per pixel (LonDPP)
     *     possible, while still covering less than or equal to the amount of longitudinal distance
     *     per pixel in the query box for the user viewport size.</li>
     *     <li>Contains all tiles that intersect the query bounding box that fulfill the above
     *     condition.</li>
     *     <li>The tiles must be arranged in-order to reconstruct the full image.</li>
     * </ul>
     * @param params The RasterRequestParams containing coordinates of the query box and the browser
     *               viewport width and height.
     * @return A valid RasterResultParams containing the computed results.
     */
    public RasterResultParams getMapRaster(RasterRequestParams params) {
        RasterResultParams.Builder resultBuild = new RasterResultParams.Builder();

        //check if query box falls within parameters
        if (params.lrlon < params.ullon || params.lrlat > params.ullat) {
            return RasterResultParams.queryFailed();
        }

        //check if there is anything to raster
        if (params.lrlon > MapServer.ROOT_LRLON || params.lrlat < MapServer.ROOT_LRLAT
                || params.ullon < MapServer.ROOT_ULLON || params.ullat > MapServer.ROOT_ULLAT) {
            return RasterResultParams.queryFailed();
        }

        //set query to be successful
        resultBuild.setQuerySuccess(true);

        //initialize renderGrid
        String[][] renderGrid;

        //calculate depth
        double userRes = lonDPP(params.lrlon, params.ullon, params.w);
        int depth = depthFind(userRes, MapServer.ROOT_LRLON, MapServer.ROOT_ULLON, -1);
        resultBuild.setDepth(depth);

        //calculate number of squares and increment levels per square
        int squares = numOfSquares(depth);
        double incrementLong = increment(squares, MapServer.ROOT_ULLON, MapServer.ROOT_LRLON);
        double incrementLat = increment(squares, MapServer.ROOT_LRLAT, MapServer.ROOT_ULLAT);

        //calculate upper left file's X and Y
        int x = upperLeftX(params.ullon, incrementLong, MapServer.ROOT_ULLON, squares);
        int y = upperLeftY(params.ullat, incrementLat, MapServer.ROOT_ULLAT, squares);

        //calculate grid height and width
        double upperlong = calcupperlong(x, incrementLong, MapServer.ROOT_ULLON);
        double upperlat = calcupperlat(y, incrementLat, MapServer.ROOT_ULLAT);
        int renderGridHeight = renderGridSizeH(incrementLat, params.lrlat, upperlat);
        int renderGridWidth = renderGridSizeW(incrementLong, params.lrlon, upperlong);
        renderGrid = new String[renderGridHeight][renderGridWidth];

        //calculate coordinates of tiles required for query box
        resultBuild.setRasterUlLon(upperlong);
        resultBuild.setRasterUlLat(upperlat);
        resultBuild.setRasterLrLon(calcLowerLong(x + renderGridWidth - 1, incrementLong,
                MapServer.ROOT_ULLON));
        resultBuild.setRasterLrLat(calcLowerLat(y + renderGridHeight - 1, incrementLat,
                MapServer.ROOT_ULLAT));

        //fill in renderGrid with corresponding files
        renderGrid = renderGridFill(renderGrid, x, y, renderGridHeight, renderGridWidth, depth);
        resultBuild.setRenderGrid(renderGrid);

        //return built RasterResultParams
        return resultBuild.create();
    }

    //finds depth of pngimage
    public int depthFind(double userRes, double lrlon, double ullon, int depth) {
        double fileRes = lonDPP(lrlon, ullon, 256);
        depth++;
        if (depth == MAX_DEPTH || fileRes <= userRes) {
            return depth;
        }
        double height =  lrlon - ullon;
        return depthFind(userRes, lrlon, ullon + (height / 2), depth);
    }

    //calculates the upper left file's X number, given the user's upper left longitude
    public int upperLeftX(double ullon, double increment, double start, int squares) {
        double begin = start;
        double end = start + increment;
        int x = 0;
        for (int i = 0; i < squares; i++) {
            if (ullon > begin && ullon < end) {
                return x;
            }
            x++;
            begin += increment;
            end += increment;
        }
        return squares - 1;
    }

    //calculates the upper left file's Y number, given the user's upper left latitude
    public int upperLeftY(double ullat, double increment, double start, int squares) {
        double begin = start;
        double end = start - increment;
        int y = 0;
        for (int i = 0; i < squares; i++) {
            if (ullat < begin && ullat > end) {
                return y;
            }
            y++;
            begin -= increment;
            end -= increment;
        }
        return squares - 1;
    }

    //finds numbers of tiles on a map's edge
    public int numOfSquares(int depth) {
        return (int) (Math.pow(2, depth));
    }

    //finds how many degrees (width or height) each square contains
    public double increment(int squares, double ul, double lr) {
        return (lr - ul) / squares;
    }

    //calculates renderGrid width size, essentially calculating how many images are necessary
    public int renderGridSizeW(double increment, double greater, double curr) {
        int size = 0;
        while (curr < greater) {
            curr += increment;
            size++;
        }
        return size;
    }

    //calculates renderGrid height size, essentially calculating how many images are necessary
    public int renderGridSizeH(double increment, double smaller, double curr) {
        int size = 0;
        while (curr > smaller) {
            curr -= increment;
            size++;
        }
        return size;
    }

    //fill in renderGrid with corresponding files
    public String[][] renderGridFill(String[][] renderGrid, int x, int y, int height,
                                     int width, int depth) {
        for (int i = 0; i < height; i++) {
            int temp = x;
            for (int j = 0; j < width; j++) {
                renderGrid[i][j] = "d" + depth + "_x" + temp + "_y" + y + ".png";
                temp++;
            }
            y++;
        }
        return renderGrid;
    }

    //calculate upper left tile's upper left longitude
    public double calcupperlong(int x, double increment, double root) {
        return root + (increment * x);
    }

    //calculate upper left tile's upper left latitude
    public double calcLowerLat(int y, double increment, double root) {
        return root - (increment * y) - increment;
    }

    //calculate lower right tile's lower right longitude
    public double calcLowerLong(int x, double increment, double root) {
        return root + (increment * x) + increment;
    }

    //calculate lower right tile's lower right latitude
    public double calcupperlat(int y, double increment, double root) {
        return root - (increment * y);
    }



    /**
     * Calculates the lonDPP of an image or query box
     * @param lrlon Lower right longitudinal value of the image or query box
     * @param ullon Upper left longitudinal value of the image or query box
     * @param width Width of the query box or image
     * @return lonDPP
     */
    private double lonDPP(double lrlon, double ullon, double width) {
        return (lrlon - ullon) / width;
    }
}
