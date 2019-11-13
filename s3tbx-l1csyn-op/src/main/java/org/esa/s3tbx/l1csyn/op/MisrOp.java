package org.esa.s3tbx.l1csyn.op;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.FlagCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
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
        description = "Coregister OLCI and SLSTR L1 Products using TreeMap from MISR product."
)
public class MisrOp extends Operator {

    @SourceProduct(alias = "olciSource", description = "OLCI source product")
    private Product olciSourceProduct;

    @SourceProduct(alias = "slstrSource", description = "SLSTR source product")
    private Product slstrSourceProduct;

    @Parameter(alias = "pixelMap", description = "Map between SLSTR Image grid and OLCI Image grid")
    private TreeMap treeMap;

    @Parameter(alias = "duplicate", description = "If true empty pixels after MISR will be filled with neighboring values")
    private boolean duplicate;

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

    /*
    @Override
    public void doExecute(ProgressMonitor pm){
        //use in order to create duplicates

        for (Band targetBand : targetProduct.getBands()){
            if (slstrSourceProduct.getBand(targetBand.getName())!= null){
                targetBand.ensureRasterData();

                Band slstrBand = slstrSourceProduct.getBand(targetBand.getName());

                //targetBand.setPixelDouble(1,1,100);
                MultiLevelImage targetImage =  targetBand.getSourceImage();


                 Dimension tileSize = ImageManager.getPreferredTileSize(targetProduct);

                final int numXTiles = MathUtils.ceilInt(targetProduct.getSceneRasterWidth() / (double) tileSize.width);
                final int numYTiles = MathUtils.ceilInt(targetProduct.getSceneRasterHeight() / (double) tileSize.height);


                //final int numXTiles = targetImage.getNumXTiles();
                //final int numYTiles = targetImage.getNumYTiles();
                //final int tileWidth = targetImage.getTileWidth();
                //final int tileHeight = targetImage.getTileHeight();

                for (int tileX = 0; tileX < numXTiles; tileX++) {
                    for (int tileY = 0; tileY < numYTiles; tileY++) {
                        //Tile targetTile = (Tile) targetImage.getTile(tileX,tileY);
                        //computeTile(targetBand,targetTile,null);
                    }
                }


                // working but has memory issues //
                /*
                for (int y = 0 ; y< targetBand.getRasterHeight() ; y++ ){
                    for (int x = 0 ; x< targetBand.getRasterWidth() ; x++ ) {
                        int[] position = {x, y};
                        int[] slstrGridPosition = (int[]) treeMap.get(position);
                        if (slstrGridPosition != null) {
                            double reflecValue = slstrBand.getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]);
                            targetBand.setPixelDouble(x,y,reflecValue);
                        }
                    }
                }
                if (duplicate == true) {
                double duplicatePixel = targetBand.getNoDataValue();
                for (int y = 0 ; y< targetBand.getRasterHeight() ; y++ ) {
                    for (int x = 0 ; x< targetBand.getRasterWidth() ; x++) {
                        if (targetBand.getPixelDouble(x,y)!=targetBand.getNoDataValue()){
                            duplicatePixel = targetBand.getPixelDouble(x,y);
                        }
                        else {
                            targetBand.setPixelDouble(x,y,duplicatePixel);
                        }
                    }
                    duplicatePixel = targetBand.getNoDataValue();
                }
                }


            }

        }

    }*/

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
                            double reflecValue = slstrSourceProduct.getRasterDataNode(targetBand.getName()).getSampleFloat(slstrGridPosition[0], slstrGridPosition[1]); // / slstrSourceProduct.getRasterDataNode(targetBand.getName()).getScalingFactor();
                            targetTile.setSample(x,y, reflecValue);
                        }
                    }
                }
            }
            if (duplicate) {
                for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                    double duplicatePixel = targetBand.getNoDataValue();
                    for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                        if (targetTile.getSampleDouble(x, y) != targetBand.getNoDataValue()) {
                            duplicatePixel = targetTile.getSampleDouble(x, y);
                        } else {
                            targetTile.setSample(x, y, duplicatePixel);
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
                        targetTile.setSample(x,y,1);
                    }
                    else {
                        targetTile.setSample(x,y,0);
                    }
                }
            }
        }
        else if (targetBand.getName().equals("duplicate_flags")){
            for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                    int[] position = {x, y};
                    int[] slstrGridPosition = (int[]) treeMap.get(position);
                    if (slstrGridPosition == null) {
                        targetTile.setSample(x,y,1);
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

        //TiePointGrid sourceGrid = olciSourceProduct.getTiePointGridAt(0);

        for (Band slstrBand : slstrSourceProduct.getBands()) {
            //if (slstrBand.getRasterWidth()== slstrSourceProduct.getBand("S5_radiance_an").getRasterWidth() ||
            //       slstrBand.getRasterHeight()== slstrSourceProduct.getBand("S5_radiance_an").getRasterHeight()) {
            Band copiedBand = targetProduct.addBand(slstrBand.getName(), ProductData.TYPE_FLOAT32);
            targetProduct.getBand(slstrBand.getName()).setNoDataValue(slstrSourceProduct.getBand(slstrBand.getName()).getNoDataValue());
            targetProduct.getBand(slstrBand.getName()).setNoDataValueUsed(true);
            // }
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

        targetProduct.addMask("MISR pixel applied",  "misr_flags == 1",
                "MISR information was used to get value of this pixel", Color.RED, 0.5);

        targetProduct.addBand(misrFlags);

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

            targetProduct.addMask("Duplicated pixel after MISR", "duplicate_flags == 1",
                    "After applying misregistration, this pixel is a duplicate of its neighbour", Color.BLUE, 0.5);

            targetProduct.addBand(duplicateFlags);
        }
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
