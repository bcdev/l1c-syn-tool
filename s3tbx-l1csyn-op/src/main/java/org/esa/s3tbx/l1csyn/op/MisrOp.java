package org.esa.s3tbx.l1csyn.op;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
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
import java.util.TreeMap;


@OperatorMetadata(alias = "Misregister",
        category = "Raster/Geometric",
        version = "1.0",
        authors = "Roman Shevchuk, Marco Peters",
        copyright = "(c) 2019 by Brockmann Consult",
        description = "Coregister OLCI and SLSTR L1 Products with TreeMap."
)
public class MisrOp extends Operator {

    @SourceProduct(alias = "olciSource", description = "OLCI source product")
    private Product olciSourceProduct;

    @SourceProduct(alias = "slstrSource", description = "SLSTR source product")
    private Product slstrSourceProduct;

    @Parameter(alias = "pixelMap", description = "Map between SLSTR Image grid and OLCI Image grid")
    private TreeMap treeMap;

    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {
        createTargetProduct();
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        Map<Band, Tile> internalTargetTiles = new HashMap<>(targetTiles);
        Tile sourceTile;
        pm.beginTask("Performing Misregestration", internalTargetTiles.size());
        try {
            for (Map.Entry<Band, Tile> entry : internalTargetTiles.entrySet()) {
                checkForCancellation();
                Band targetBand = entry.getKey();
                Tile targetTile = entry.getValue();

                if (olciSourceProduct.containsBand(targetBand.getName())) {
                    sourceTile = getSourceTile(olciSourceProduct.getRasterDataNode(targetBand.getName()), targetRectangle);
                    for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                        for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                            double reflecValue = sourceTile.getSampleDouble(x, y);
                            targetTile.setSample(x, y, reflecValue);
                        }
                    }
                } else if (slstrSourceProduct.containsBand(targetBand.getName())) {
                    for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                        for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                            int[] position = {x, y};
                            int[] slstrGridPosition = (int[]) treeMap.get(position);
                            if (slstrGridPosition != null) {
                                double reflecValue = slstrSourceProduct.getRasterDataNode(targetBand.getName()).getSampleFloat(slstrGridPosition[0],slstrGridPosition[1]);
                                targetTile.setSample(x, y, reflecValue);
                            }
                        }
                    }
                }
                else {
                    throw new OperatorException("Band copying errod");
                }
            }
            pm.worked(1);
        } finally {
            pm.done();
        }
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm){
        Tile sourceTile;
        Rectangle targetRectangle = targetTile.getRectangle();
        if (olciSourceProduct.containsBand(targetBand.getName())) {
            sourceTile = getSourceTile(olciSourceProduct.getRasterDataNode(targetBand.getName()), targetRectangle);
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    double reflecValue = sourceTile.getSampleDouble(x, y);
                    targetTile.setSample(x, y, reflecValue);
                }
            }
        } else if (slstrSourceProduct.containsBand(targetBand.getName())) {
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    targetTile.setSample(x,y,targetBand.getNoDataValue());
                    if (slstrSourceProduct.getBand(targetBand.getName()).getRasterWidth() == slstrSourceProduct.getBand("S5_radiance_an").getRasterWidth()) {

                        int[] position = {x, y};
                        int[] slstrGridPosition = (int[]) treeMap.get(position);
                        if (slstrGridPosition != null) {
                            float reflecValue = slstrSourceProduct.getRasterDataNode(targetBand.getName()).getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]); // / slstrSourceProduct.getRasterDataNode(targetBand.getName()).getScalingFactor();
                            targetTile.setSample(x,y, reflecValue);
                        }
                    }
                }
            }
        }
        else if (targetBand.getName().equals("misr_flags")){
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    int[] position = {x, y};
                    int[] slstrGridPosition = (int[]) treeMap.get(position);
                    if (slstrGridPosition != null ) {
                    targetTile.setSample(x,y,slstrGridPosition[0]+slstrGridPosition[1]);
                    }
                }
                }
        }
    }


    private void createTargetProduct() {
        targetProduct = new Product(olciSourceProduct.getName(), olciSourceProduct.getProductType(),
                olciSourceProduct.getSceneRasterWidth(),
                olciSourceProduct.getSceneRasterHeight());


        for (Band olciBand : olciSourceProduct.getBands()) {
            ProductUtils.copyBand(olciBand.getName(), olciSourceProduct, targetProduct, true);
        }

        TiePointGrid sourceGrid = olciSourceProduct.getTiePointGridAt(0);

        for (Band slstrBand : slstrSourceProduct.getBands()) {
            if (slstrBand.getRasterWidth()== slstrSourceProduct.getBand("S5_radiance_an").getRasterWidth() ||
                    slstrBand.getRasterHeight()== slstrSourceProduct.getBand("S5_radiance_an").getRasterHeight()) {
                Band copiedBand = targetProduct.addBand(slstrBand.getName(), ProductData.TYPE_FLOAT32);
                targetProduct.getBand(slstrBand.getName()).setNoDataValue(slstrSourceProduct.getBand(slstrBand.getName()).getNoDataValue());
            }
        }

        ProductUtils.copyMetadata(olciSourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(olciSourceProduct, targetProduct);
        ProductUtils.copyMasks(olciSourceProduct, targetProduct);
        ProductUtils.copyFlagBands(olciSourceProduct, targetProduct, true);
        ProductUtils.copyGeoCoding(olciSourceProduct, targetProduct);

        final FlagCoding flagCoding = new FlagCoding("MISR_Applied");
        flagCoding.setDescription("MISR processor flag");
        targetProduct.getFlagCodingGroup().add(flagCoding);
        Band misrFlags = new Band("misr_flags", ProductData.TYPE_UINT32,
                olciSourceProduct.getSceneRasterWidth(),
                olciSourceProduct.getSceneRasterHeight());
        //misrFlags.setSampleCoding(flagCoding);

        targetProduct.addMask("MISR pixel applied",  "misr_flags",
                "MISR information was used to get value of this pixel", Color.RED, 0.5);

        targetProduct.addBand(misrFlags);
        /*ProductUtils.copyMetadata(slstrSourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(slstrSourceProduct, targetProduct);
        ProductUtils.copyMasks(slstrSourceProduct, targetProduct);
        ProductUtils.copyFlagBands(slstrSourceProduct, targetProduct, false);
        ProductUtils.copyGeoCoding(slstrSourceProduct, targetProduct);
        targetProduct.setAutoGrouping(olciSourceProduct.getAutoGrouping().toString()); */

    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MisrOp.class);
        }
    }

}
