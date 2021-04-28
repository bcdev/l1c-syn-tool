package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.jetbrains.annotations.NotNull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.List;

public class RandomSamplerTest {

    public static void main(String[] args) throws IOException {
        final String path = args[0];
        final File root = new File(path);
        final Map<String, String[]> list = new TreeMap<>();

        if (root.isDirectory()) {
            for (final File sceneDir : Objects.requireNonNull(root.listFiles(File::isDirectory))) {
                final File[] files = Objects.requireNonNull(sceneDir.listFiles(file -> file.getName().endsWith(".xml")));
                for (final File file : files) {
                    final Product product = ProductIO.readProduct(file, "Sen3");
                    if (product == null) {
                        continue;
                    }
                    try {
                        final Coordinate[] boundingBox = BoundingBoxTest.BoundingBox.create(product);
                        final Coordinate[] coordinates = RandomSampler.createRandomSamples(product, boundingBox);
                        final List<String> lineList = new ArrayList<>();
                        for (int i = 0; i < coordinates.length; i++) {
                            final Coordinate coordinate = coordinates[i];
                            lineList.add(String.format("P%d\t%s\t%s\n", i, coordinate.getX(), coordinate.getY()));
                        }
                        list.put(sceneDir.getName(), lineList.toArray(new String[0]));
                    } finally {
                        product.closeIO();
                    }
                }
            }
        }
        for (final Map.Entry<String, String[]> entry : list.entrySet()) {
            final String filename = entry.getKey().replace("SEN3", "txt");
            System.out.println("filename = " + filename);
            try (final Writer writer = new FileWriter(filename)) {
                writer.write("Name\tLon\tLat\n");
                for (final String line : entry.getValue()) {
                    writer.write(line);
                }
            }
        }
    }

    private static class RandomSampler {

        public static Coordinate[] createRandomSamples(Product product, Coordinate[] boundingBox) {
            final double minLon = Math.min(boundingBox[0].getX(), boundingBox[1].getX());
            final double maxLon = Math.max(boundingBox[2].getX(), boundingBox[3].getX());
            final double minLat = Math.min(boundingBox[1].getY(), boundingBox[2].getY());
            final double maxLat = Math.max(boundingBox[0].getY(), boundingBox[3].getY());
            final GeometryFactory geometryFactory = new GeometryFactory();
            final Polygon polygon = geometryFactory.createPolygon(boundingBox);
            final GeoCoding geoCoding = product.getSceneGeoCoding();
            final Random random = new Random(5489);

            final List<Coordinate> coordinateList = new ArrayList<>(10000);
            while (coordinateList.size() < 10000) {
                final double lon = minLon + random.nextDouble() * (maxLon - minLon);
                final double lat = minLat + random.nextDouble() * (maxLat - minLat);
                final Coordinate location = new Coordinate(lon, lat);
                if (polygon.contains(geometryFactory.createPoint(location))) {
                    final Coordinate pixelLocation = getPixelLocation(geoCoding, lon, lat);
                    if (polygon.contains(geometryFactory.createPoint(pixelLocation))) {
                        coordinateList.add(pixelLocation);
                    }
                }
            }
            return coordinateList.toArray(new Coordinate[0]);
        }

        @NotNull
        private static Coordinate getPixelLocation(GeoCoding geoCoding, double lon, double lat) {
            final GeoPos g = geoCoding.getGeoPos(geoCoding.getPixelPos(new GeoPos(lat, lon), new PixelPos()), new GeoPos());
            return new Coordinate(g.getLon(), g.getLat());
        }
    }
}
