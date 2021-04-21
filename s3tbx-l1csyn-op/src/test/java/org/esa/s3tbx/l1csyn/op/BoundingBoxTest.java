package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class is used for the SYN-CM project (shall be copied to there but repository does not exist yet).
 *
 * @author Ralf Quast
 */
public class BoundingBoxTest {

    public static void main(String[] args) throws IOException {
        final String path = args[0];
        final File root = new File(path);
        final Map<String, String> report = new TreeMap<>();

        if (root.isDirectory()) {
            for (final File sceneDir : Objects.requireNonNull(root.listFiles(File::isDirectory))) {
                final File[] files = Objects.requireNonNull(sceneDir.listFiles(file -> file.getName().endsWith(".nc")));
                for (final File file : files) {
                    final Product product = ProductIO.readProduct(file, "NetCDF4-BEAM");
                    try {
                        report.put(sceneDir.getName(), new BoundingBox(product).toWKT());
                    } finally {
                        product.closeIO();
                    }
                }
            }
        }
        for (final Map.Entry<String, String> entry : report.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    private static class BoundingBox {
        private final GeoCoding geoCoding;
        private final int w;
        private final int h;

        public BoundingBox(Product product) {
            geoCoding = product.getSceneGeoCoding();
            w = product.getSceneRasterWidth();
            h = product.getSceneRasterHeight();
        }

        private Coordinate getCoordinate(double x, double y) {
            final GeoPos geoPos = geoCoding.getGeoPos(new PixelPos(x, y), new GeoPos());
            return new Coordinate(geoPos.getLon(), geoPos.getLat());
        }

        public String toWKT() {  // inner bounding box as WKT
            final List<Coordinate> coordinateList = new ArrayList<>(4);
            coordinateList.add(getCoordinate(0.5, 0.5));
            coordinateList.add(getCoordinate(0.5, h - 0.5));
            coordinateList.add(getCoordinate(w - 0.5, h - 0.5));
            coordinateList.add(getCoordinate(w - 0.5, 0.5));
            coordinateList.add(getCoordinate(0.5, 0.5));
            return new GeometryFactory().createPolygon(coordinateList.toArray(new Coordinate[5])).toText();
        }
    }
}
