package org.esa.s3tbx.l1csyn.op;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.DistanceMeasure;
import org.esa.snap.core.util.math.EuclideanDistance;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;


@OperatorMetadata(alias = "Misregister",
        internal = true,
        category = "Raster/Geometric",
        version = "1.0",
        authors = "Roman Shevchuk, Marco Peters",
        copyright = "(c) 2019 by Brockmann Consult",
        description = "Coregister OLCI and SLSTR L1 Products using TreeMaps from MISR product. At minimum one Treemap for oblique and one for nadir view must be provided."
)
public class MisrOp extends Operator {

    private static final String PROP_DEBUG_OUTPUT_DIR = "l1c.debug.outputDir";

    @SourceProduct(alias = "olciSource", description = "OLCI source product")
    private Product olciSourceProduct;

    @SourceProduct(alias = "slstrSource", description = "SLSTR source product")
    private Product slstrSourceProduct;

    @Parameter(alias = "duplicate", description = "If set to true empty pixels after MISR will be filled with neighboring values")
    private boolean duplicate;

    @Parameter(alias = "orphan", description = "If set to true orphan pixels will be used",
            defaultValue = "false")
    private boolean orphan;

    @Parameter(alias = "singlePixelMap", description = "If set to true, than single pixel map will be used for coregistering all SLSTR bands. Otherwise user shall provide all maps separately",
            defaultValue = "true")
    private boolean singlePixelMap;

    @Parameter(alias = "S1PixelMap", description = "Pixel map for S1 nadir view")
    private Map<int[], int[]> S1PixelMap;

    @Parameter(alias = "S2PixelMap", description = "Pixel map for S2 nadir view")
    private Map<int[], int[]> S2PixelMap;

    @Parameter(alias = "S3PixelMap", description = "Pixel map for S3 nadir view")
    private Map<int[], int[]> S3PixelMap;

    @Parameter(alias = "S4PixelMap", description = "Pixel map for S4 nadir view")
    private Map<int[], int[]> S4PixelMap;

    @Parameter(alias = "S5PixelMap", description = "Pixel map for S5 nadir view")
    private Map<int[], int[]> S5PixelMap;

    @Parameter(alias = "S6PixelMap", description = "Pixel map for S6 nadir view")
    private Map<int[], int[]> S6PixelMap;

    @Parameter(alias = "aoPixelMap", description = "Pixel map for ao oblique view")
    private Map<int[], int[]> aoPixelMap;

    @Parameter(alias = "boPixelMap", description = "Pixel map for bo oblique view")
    private Map<int[], int[]> boPixelMap;

    @Parameter(alias = "coPixelMap", description = "Pixel map for co oblique view")
    private Map<int[], int[]> coPixelMap;

    @Parameter(alias = "S1OrphanMap", description = "Orphan pixel map for S1 nadir view")
    private Map<int[], int[]> S1OrphanMap;

    @Parameter(alias = "S2OrphanMap", description = "Orphan pixel map for S2 nadir view")
    private Map<int[], int[]> S2OrphanMap;

    @Parameter(alias = "S3OrphanMap", description = "Orphan pixel map for S3 nadir view")
    private Map<int[], int[]> S3OrphanMap;

    @Parameter(alias = "S4OrphanMap", description = "Orphan pixel map for S4 nadir view")
    private Map<int[], int[]> S4OrphanMap;

    @Parameter(alias = "S5OrphanMap", description = "Orphan pixel map for S5 nadir view")
    private Map<int[], int[]> S5OrphanMap;

    @Parameter(alias = "S6OrphanMap", description = "Orphan pixel map for S6 nadir view")
    private Map<int[], int[]> S6OrphanMap;

    @Parameter(alias = "aoOrphanMap", description = "Orphan pixel map for ao oblique view")
    private Map<int[], int[]> aoOrphanMap;

    @Parameter(alias = "boOrphanMap", description = "Orphan pixel map for bo oblique view")
    private Map<int[], int[]> boOrphanMap;

    @Parameter(alias = "coPixelMap", description = "Orphan pixel map for co oblique view")
    private Map<int[], int[]> coOrphanMap;
    @TargetProduct
    private Product targetProduct;
    private Map<String, PrintStream> fileMap;

    @Override
    public void initialize() throws OperatorException {
        fileMap = Collections.synchronizedMap(new HashMap<>());
        if (singlePixelMap == true) {
            this.S1PixelMap = S3PixelMap;
            this.S2PixelMap = S3PixelMap;
            this.S3PixelMap = S3PixelMap;
            this.S4PixelMap = S3PixelMap;
            this.S5PixelMap = S3PixelMap;
            this.S6PixelMap = S3PixelMap;
            this.aoPixelMap = aoPixelMap;
            this.boPixelMap = aoPixelMap;
            this.coPixelMap = aoPixelMap;
            this.S1OrphanMap = S3OrphanMap;
            this.S2OrphanMap = S3OrphanMap;
            this.S3OrphanMap = S3OrphanMap;
            this.S4OrphanMap = S3OrphanMap;
            this.S5OrphanMap = S3OrphanMap;
            this.S6OrphanMap = S3OrphanMap;
            this.aoOrphanMap = aoPixelMap;
            this.boOrphanMap = aoOrphanMap;
            this.coOrphanMap = aoOrphanMap;
        }
        createTargetProduct();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) {
        Map<int[], int[]> map = new HashMap<>();
        Map<int[], int[]> mapOrphan = new HashMap<>();
        Rectangle targetRectangle = targetTile.getRectangle();


        if (targetBand.getName().contains("_ao")) {
            map = aoPixelMap;
            mapOrphan = aoOrphanMap;
        } else if (targetBand.getName().contains("_bo")) {
            map = boPixelMap;
            mapOrphan = boOrphanMap;
        } else if (targetBand.getName().contains("_co")) {
            map = coPixelMap;
            mapOrphan = coOrphanMap;
        } else if ((targetBand.getName().contains("S1") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S1") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S1") && targetBand.getName().contains("_cn"))) {
            map = S1PixelMap;
            mapOrphan = S1OrphanMap;
        } else if ((targetBand.getName().contains("S2") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S2") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S2") && targetBand.getName().contains("_cn"))) {
            map = S2PixelMap;
            mapOrphan = S2OrphanMap;
        } else if ((targetBand.getName().contains("S3") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S3") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S3") && targetBand.getName().contains("_cn"))) {
            map = S3PixelMap;
            mapOrphan = S3OrphanMap;
        } else if ((targetBand.getName().contains("S4") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S4") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S4") && targetBand.getName().contains("_cn"))) {
            map = S4PixelMap;
            mapOrphan = S4OrphanMap;
        } else if ((targetBand.getName().contains("S5") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S5") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S5") && targetBand.getName().contains("_cn"))) {
            map = S5PixelMap;
            mapOrphan = S5OrphanMap;
        } else if ((targetBand.getName().contains("S6") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S6") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S6") && targetBand.getName().contains("_cn"))) {
            map = S6PixelMap;
            mapOrphan = S6OrphanMap;
        } else if ((targetBand.getName().contains("_an") || targetBand.getName().contains("_bn") || targetBand.getName().contains("_cn"))) {
            map = S3PixelMap;
            mapOrphan = S3OrphanMap;
        }

        RasterDataNode oa17_radiance = olciSourceProduct.getRasterDataNode("Oa17_radiance");
        if (slstrSourceProduct.containsBand(targetBand.getName())) {
            Band sourceBand = slstrSourceProduct.getBand(targetBand.getName());
            int sourceRasterWidth = sourceBand.getRasterWidth();
            int sourceRasterHeight = sourceBand.getRasterHeight();
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    targetTile.setSample(x, y, targetBand.getNoDataValue());
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition != null) {
                        if (slstrGridPosition[0] < sourceRasterWidth && slstrGridPosition[1] < sourceRasterHeight) {
                            try {
                                double reflecValue = sourceBand.getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]); // / slstrSourceProduct.getRasterDataNode(targetBand.getName()).getScalingFactor();
                                if (reflecValue < 0) {
                                    reflecValue = targetBand.getNoDataValue();
                                }
                                targetTile.setSample(x, y, reflecValue);
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
            //Orphan pixels
            if (orphan) {
                String parentPath = slstrSourceProduct.getFileLocation().getParent();
                String netcdfDataPath = parentPath + "/" + targetBand.getName() + ".nc";
                if (Files.exists(Paths.get(netcdfDataPath))) {
                    try {
                        NetcdfFile netcdf = NetcdfFileOpener.open(netcdfDataPath);
                        Variable orphanVariable = netcdf.findVariable(targetBand.getName().replace("radiance_", "radiance_orphan_"));
                        final Attribute scale_factorAttribute = orphanVariable.findAttribute("scale_factor");
                        double scaleFactor = 1.0;
                        if (scale_factorAttribute != null) {
                            final Number scale_factor = scale_factorAttribute.getNumericValue();
                            if (scale_factor != null) {
                                scaleFactor = (double) scale_factor;
                            }
                        }


                        final Array orphanData = orphanVariable.read();
                        final int[] dataShape = orphanData.getShape(); // shape is [2400, 374] for S3_radiance_orphan_an
                        final Index rawIndex = orphanData.getIndex();
                        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                                int[] position = {x, y};
                                int[] slstrOrphanPosition = mapOrphan.get(position);
                                if (slstrOrphanPosition != null) {
                                    final int orphanPosX = slstrOrphanPosition[0];
                                    final int orphanPosY = slstrOrphanPosition[1];
                                    if (orphanPosX < dataShape[0] && orphanPosY < dataShape[1]) {
                                        // Todo(mp,Feb-2021) - this leads to an ArrayIndexOutOfBoundsException in the oblique case
                                        try {
                                            rawIndex.set(orphanPosY, orphanPosX); // Dimension is Y, X  --> so 'wrong' order of Y and X here
                                        } catch (ArrayIndexOutOfBoundsException aioobe) {
                                            // ignore for now; stop processing this line
                                            break;
                                        }
                                        double dataValue = orphanData.getDouble(rawIndex);
                                        if (dataValue > 0) {
                                            targetTile.setSample(x, y, dataValue * scaleFactor);
                                        }
                                    }
                                }
                            }
                        }
                        netcdf.close();
                    } catch (IOException ioe) {
                        SystemUtils.LOG.log(Level.WARNING, String.format("Could not process file %s: %s", netcdfDataPath, ioe.getMessage()));
                    }
                } else {
                    SystemUtils.LOG.log(Level.FINE, String.format("File %s does not exist", netcdfDataPath));
                }
            }
            //
            if (duplicate) {
                //Commented, because other way of filling holes will be implemented. TODO: Remove later
                /*for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    double duplicatePixel = getDuplicatedPixel(targetRectangle.x, y, targetBand, map, sourceBand);
                    //double duplicatePixel = 0d ;
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        if (targetTile.getSampleDouble(x, y) != targetBand.getNoDataValue()) {
                            duplicatePixel = targetTile.getSampleDouble(x, y);
                        } else {
                            targetTile.setSample(x, y, duplicatePixel);
                        }
                    }
                }
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        if (!oa17_radiance.isPixelValid(x, y)) {
                            targetTile.setSample(x, y, targetBand.getNoDataValue());
                        }
                    }
                }*/
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        {
                            if (targetTile.getSampleDouble(x, y) == targetBand.getNoDataValue()) {
                                double neighborPixel = getNeighborPixel(x, y, targetBand, map, sourceBand);
                                targetTile.setSample(x, y, neighborPixel);
                            }
                        }
                    }
                }
            }

        } else if (targetBand.getName().equals("misr_flags")) {
            map = S3PixelMap;
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition != null) {
                        targetTile.setSample(x, y, 1);
                    } else {
                        targetTile.setSample(x, y, 0);
                    }
                }
            }
        } else if (targetBand.getName().equals("duplicate_flags")) {
            map = S3PixelMap;
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    int numNeigbors = getNumberNeighbors(x, y, targetBand, map);
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition == null && oa17_radiance.isPixelValid(x, y)) {
                        targetTile.setSample(x, y, numNeigbors);
                    }
                }
            }
        }
    }

    @Override
    public void dispose() {
        for (PrintStream stream : fileMap.values()) {
            stream.close();
        }
    }

    private void writeToFile(String key, String text) throws IOException {
        final String outputDir = System.getProperty(PROP_DEBUG_OUTPUT_DIR);
        if (outputDir == null) {
            return;
        }
        if (!fileMap.containsKey(key)) {
            fileMap.put(key, new PrintStream(Files.newOutputStream(Paths.get(outputDir + key + ".txt"))));
        }
        PrintStream outStream = fileMap.get(key);
        outStream.print(text);
        outStream.flush();
    }

    private double getDuplicatedPixel(int x, int y, Band targetBand, Map<int[], int[]> map, Band sourceBand) {
        int rasterWidth = sourceBand.getRasterWidth();
        int rasterHeight = sourceBand.getRasterHeight();
        double duplicatePixel;
        int[] position = {x, y};
        int[] slstrGridPosition = map.get(position);
        if (slstrGridPosition != null && slstrGridPosition[0] < rasterWidth && slstrGridPosition[1] < rasterHeight) {
            duplicatePixel = sourceBand.getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]);
        } else {
            while (slstrGridPosition == null && x > 0) {
                x = x - 1;
                int[] tempPosition = {x, y};
                slstrGridPosition = map.get(tempPosition);
            }
            if (slstrGridPosition != null && slstrGridPosition[0] < rasterWidth && slstrGridPosition[1] < rasterHeight) {
                duplicatePixel = sourceBand.getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]);
            } else {
                duplicatePixel = targetBand.getNoDataValue();
            }
        }
        if (duplicatePixel < 0) {
            return targetBand.getNoDataValue();
        }
        return duplicatePixel;
    }

    private double getNeighborPixel(int x, int y, Band targetBand, Map<int[], int[]> map, Band sourceBand) {

        double neighborPixel = targetBand.getNoDataValue();
        int[] position = {x, y};
        int[] slstrGridPosition = map.get(position);
        GeoPos pixelGeoPos = targetBand.getGeoCoding().getGeoPos(new PixelPos(x, y), null);
        if (slstrGridPosition != null) {
            return sourceBand.getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]);
        } else {
            EuclideanDistance euclideanDistance = new EuclideanDistance(pixelGeoPos.getLon(), pixelGeoPos.getLat());
            for (int size = 3; size < 10; size += 2) {
                neighborPixel = searchClosetPixel(size, sourceBand, euclideanDistance, x, y, targetBand, map);
                if (neighborPixel != targetBand.getNoDataValue()) {
                    return neighborPixel;
                }
            }
        }
        return neighborPixel;
    }

    private double searchClosetPixel(int size, Band sourceBand, DistanceMeasure distanceMeasure, int x, int y, Band targetBand, Map<int[], int[]> map) {
        double distance = Double.MAX_VALUE;
        double neighborPixel = targetBand.getNoDataValue();
        int step = size / 2;
        final GeoCoding sourceGeoCoding = sourceBand.getGeoCoding();
        for (int i = 0; i < size; i += 1) {
            for (int j = 0; j < size; j += 1) {
                int[] neighborPos = {x - step + i, y - step + j};
                int[] slstrNeighbor = map.get(neighborPos);
                if (slstrNeighbor != null) {
                    GeoPos neighborGeoPos = sourceGeoCoding.getGeoPos(new PixelPos(slstrNeighbor[0], slstrNeighbor[1]), null);
                    double neighborDist = distanceMeasure.distance(neighborGeoPos.getLon(), neighborGeoPos.getLat());
                    if (neighborDist < distance) {
                        neighborPixel = sourceBand.getSampleFloat(slstrNeighbor[0], slstrNeighbor[1]);
                        distance = neighborDist;
                    }
                }
            }
        }
        return neighborPixel;
    }


    private int getNumberNeighbors(int x, int y, Band targetBand, Map<int[], int[]> map) {
        int countFound = 0;
        int[] position = {x, y};
        int[] slstrGridPosition = map.get(position);
        if (slstrGridPosition != null) {
            return 0;
        } else {
            for (int i = 0; i < 3; i += 2) {
                for (int j = 0; j < 3; j += 2) {
                    int[] neighborPos = {x - 1 + i, y - 1 + j};
                    int[] slstrNeighbor = map.get(neighborPos);
                    if (slstrNeighbor != null) {
                        countFound += 1;
                    }
                }
            }
        }
        if (countFound > 0) {
            return countFound;
        } else {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    int[] neighborPos = {x - 2 + i, y - 2 + j};
                    int[] slstrNeighbor = map.get(neighborPos);
                    if (slstrNeighbor != null) {
                        countFound += 1;
                    }
                }
            }
        }
        return countFound;
    }

    private void createTargetProduct() {
        targetProduct = new Product(olciSourceProduct.getName(), olciSourceProduct.getProductType(),
                                    olciSourceProduct.getSceneRasterWidth(),
                                    olciSourceProduct.getSceneRasterHeight());


        for (Band olciBand : olciSourceProduct.getBands()) {
            ProductUtils.copyBand(olciBand.getName(), olciSourceProduct, targetProduct, true);
        }

        for (Band slstrBand : slstrSourceProduct.getBands()) {
            if (slstrBand.getName().contains("_an") || slstrBand.getName().contains("_bn") || slstrBand.getName().contains("_cn")
                    || slstrBand.getName().contains("_ao") || slstrBand.getName().contains("_bo") || slstrBand.getName().contains("_co")) {
                Band copiedBand = targetProduct.addBand(slstrBand.getName(), ProductData.TYPE_FLOAT32);
                copiedBand.setNoDataValue(slstrSourceProduct.getBand(slstrBand.getName()).getNoDataValue());
                copiedBand.setNoDataValueUsed(true);
            } else {
                if (!slstrBand.getName().contains("_in") && !slstrBand.getName().contains("_io") &&
                        !slstrBand.getName().contains("_fn") && !slstrBand.getName().contains("_fo")) {
                    ProductUtils.copyBand(slstrBand.getName(), slstrSourceProduct, targetProduct, true);
                }
            }
        }

        ProductUtils.copyMetadata(olciSourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(olciSourceProduct, targetProduct);
        ProductUtils.copyMasks(olciSourceProduct, targetProduct);
        ProductUtils.copyFlagBands(olciSourceProduct, targetProduct, true);
        ProductUtils.copyGeoCoding(olciSourceProduct, targetProduct);

        final FlagCoding misrFlagCoding = new FlagCoding("MISR_Applied");
        misrFlagCoding.setDescription("MISR processor flag");
        targetProduct.getFlagCodingGroup().add(misrFlagCoding);
        Band misrFlags = new Band("misr_flags", ProductData.TYPE_UINT32,
                                  olciSourceProduct.getSceneRasterWidth(),
                                  olciSourceProduct.getSceneRasterHeight());
        misrFlagCoding.addFlag("MISR not applied", 0, "MISR not Applied");
        misrFlagCoding.addFlag("MISR applied", 1, "MISR applied");
        misrFlags.setSampleCoding(misrFlagCoding);

        targetProduct.addBand(misrFlags);
        targetProduct.addMask("MISR pixel applied", "misr_flags != 0",
                              "MISR information was used to get value of this pixel", Color.RED, 0.5);

        if (duplicate) {
            //TODO: as of 2021-03-05 this shows how many neighbors were found during filling-holes algorithm. This band may be removed before release.
            /*final FlagCoding duplicateFlagCoding = new FlagCoding("Duplicated pixel after MISR");
            duplicateFlagCoding.setDescription("Duplicate pixels");
            targetProduct.getFlagCodingGroup().add(duplicateFlagCoding);
            Band duplicateFlags = new Band("duplicate_flags", ProductData.TYPE_UINT32,
                                           olciSourceProduct.getSceneRasterWidth(),
                                           olciSourceProduct.getSceneRasterHeight());
            duplicateFlagCoding.addFlag("pixel not duplicated", 0, "pixel not duplicated");
            duplicateFlagCoding.addFlag("pixel duplicated", 1, "pixel duplicated");
            duplicateFlags.setSampleCoding(duplicateFlagCoding);
            targetProduct.addBand(duplicateFlags);

            targetProduct.addMask("Duplicated pixel after MISR", "duplicate_flags != 0",
                                  "After applying misregistration, this pixel is a duplicate of its neighbour", Color.BLUE, 0.5);*/

            Band duplicateFlags = new Band("duplicate_flags", ProductData.TYPE_UINT32,
                                           olciSourceProduct.getSceneRasterWidth(),
                                           olciSourceProduct.getSceneRasterHeight());
            targetProduct.addBand(duplicateFlags);

            targetProduct.addMask("Duplicated pixel after MISR", "duplicate_flags != 0",
                                  "After applying misregistration, this pixel is a duplicate of its neighbour", Color.BLUE, 0.5);
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MisrOp.class);
        }
    }

}
