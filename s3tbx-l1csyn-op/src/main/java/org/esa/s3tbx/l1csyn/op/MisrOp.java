package org.esa.s3tbx.l1csyn.op;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;


@OperatorMetadata(alias = "Misregister",
        internal = true,
        category = "Raster/Geometric",
        version = "1.0",
        authors = "Roman Shevchuk, Marco Peters",
        copyright = "(c) 2019 by Brockmann Consult",
        description = "Coregister OLCI and SLSTR L1 Products using TreeMaps from MISR product. At minimum one Treemap for oblique and one for nadir view must be provided."
)
public class MisrOp extends Operator {

    @SourceProduct(alias = "olciSource", description = "OLCI source product")
    private Product olciSourceProduct;

    @SourceProduct(alias = "slstrSource", description = "SLSTR source product")
    private Product slstrSourceProduct;

    @Parameter(alias = "duplicate", description = "If set to true empty pixels after MISR will be filled with neighboring values")
    private boolean duplicate;

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


    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
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
        }
        createTargetProduct();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) {
        Tile sourceTile;
        Map<int[], int[]> map = new HashMap<>();
        Rectangle targetRectangle = targetTile.getRectangle();


        if (targetBand.getName().contains("_ao")) {
            map = aoPixelMap;
        } else if (targetBand.getName().contains("_bo")) {
            map = boPixelMap;
        } else if (targetBand.getName().contains("_co")) {
            map = coPixelMap;
        } else if ((targetBand.getName().contains("S1") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S1") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S1") && targetBand.getName().contains("_cn"))) {
            map = S1PixelMap;
        } else if ((targetBand.getName().contains("S2") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S2") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S2") && targetBand.getName().contains("_cn"))) {
            map = S2PixelMap;
        } else if ((targetBand.getName().contains("S3") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S3") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S3") && targetBand.getName().contains("_cn"))) {
            map = S3PixelMap;
        } else if ((targetBand.getName().contains("S4") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S4") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S4") && targetBand.getName().contains("_cn"))) {
            map = S4PixelMap;
        } else if ((targetBand.getName().contains("S5") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S5") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S5") && targetBand.getName().contains("_cn"))) {
            map = S5PixelMap;
        } else if ((targetBand.getName().contains("S6") && targetBand.getName().contains("_an")) || (targetBand.getName().contains("S6") && targetBand.getName().contains("_bn")) || (targetBand.getName().contains("S6") && targetBand.getName().contains("_cn"))) {
            map = S6PixelMap;
        } else if ((targetBand.getName().contains("_an") || targetBand.getName().contains("_bn") || targetBand.getName().contains("_cn"))) {
            map = S3PixelMap;
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
            if (duplicate) {
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
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
                    int[] position = {x, y};
                    int[] slstrGridPosition = map.get(position);
                    if (slstrGridPosition == null && oa17_radiance.isPixelValid(x, y)) {
                        targetTile.setSample(x, y, 1);
                    }
                }
            }
        }
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
                targetProduct.getBand(slstrBand.getName()).setNoDataValue(slstrSourceProduct.getBand(slstrBand.getName()).getNoDataValue());
                targetProduct.getBand(slstrBand.getName()).setNoDataValueUsed(true);
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
            final FlagCoding duplicateFlagCoding = new FlagCoding("Duplicated pixel after MISR");
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
                                  "After applying misregistration, this pixel is a duplicate of its neighbour", Color.BLUE, 0.5);
        }
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MisrOp.class);
        }
    }

}
