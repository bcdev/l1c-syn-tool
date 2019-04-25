package org.esa.s3tbx.l1csyn.op;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
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

    @SourceProduct(alias="olciSource",description = "OLCI source product")
    private Product olciSourceProduct;

    @SourceProduct(alias="slstrSource",description = "SLSTR source product")
    private Product slstrSourceProduct;

    @Parameter(alias="pixelMap", description = "Map between SLSTR Image grid and OLCI Image grid")
    private TreeMap treeMap;

    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException{
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
                }
                else if (slstrSourceProduct.containsBand(targetBand.getName())){
                    for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
                        for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                            int[] position = {x,y};
                            int[] slstrGridPosition = (int[]) treeMap.get(position);
                            if (slstrGridPosition!=null){
                                double reflecValue = slstrSourceProduct.getBand(targetBand.getName()).getPixelDouble(slstrGridPosition[0],slstrGridPosition[1]);
                                targetTile.setSample(x, y, reflecValue);
                            }
                            else{
                                targetTile.setSample(x,y,Float.NaN);
                            }
                        }
                    }
                }
            }
            pm.worked(1);
        }
        finally {
            pm.done();
        }
    }




    private  void createTargetProduct(){
        targetProduct = new Product(olciSourceProduct.getName(), olciSourceProduct.getProductType(),
                olciSourceProduct.getSceneRasterWidth(),
                olciSourceProduct.getSceneRasterHeight());


        for (Band olciBand : olciSourceProduct.getBands()){
            ProductUtils.copyBand(olciBand.getName(),olciSourceProduct,targetProduct,true);
        }

        for (Band slstrBand : slstrSourceProduct.getBands()){
            ProductUtils.copyBand(slstrBand.getName(),olciSourceProduct,targetProduct,false);
        }


        ProductUtils.copyMetadata(olciSourceProduct,targetProduct);
        ProductUtils.copyTiePointGrids(olciSourceProduct,targetProduct);
        ProductUtils.copyMasks(olciSourceProduct,targetProduct);
        ProductUtils.copyFlagBands(olciSourceProduct,targetProduct, true);
        ProductUtils.copyGeoCoding(olciSourceProduct,targetProduct);

        ProductUtils.copyMetadata(slstrSourceProduct,targetProduct);
        ProductUtils.copyTiePointGrids(slstrSourceProduct,targetProduct);
        ProductUtils.copyMasks(slstrSourceProduct,targetProduct);
        ProductUtils.copyFlagBands(slstrSourceProduct,targetProduct, false);
        ProductUtils.copyGeoCoding(slstrSourceProduct,targetProduct);
    }


    public static class Spi extends OperatorSpi {
        public Spi() {
            super(MisrOp.class);
        }
    }

}
