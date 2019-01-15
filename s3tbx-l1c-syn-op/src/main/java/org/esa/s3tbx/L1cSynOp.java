package org.esa.s3tbx;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@OperatorMetadata(alias = "L1C-SYN",
        label = "L1C-SYN Tool",
        authors = "Marco Peters",
        copyright = "Brockmann Consult GmbH",
        version = "0.1")
public class L1cSynOp extends Operator {

    @SourceProduct(alias = "olciProduct", label = "OLCI Product", description = "OLCI source product")
    private Product olciSource;

    @SourceProduct(alias = "slstrProduct", label = "SLSTR Product", description = "SLSTR source product")
    private Product slstrSource;

    @TargetProduct(label = "L1C SYN Product", description = "L1C SYNERGY output product")
    private Product reprojectedTarget;

    @Parameter(label="allowed difference between time of OLCI and SLSTR products",defaultValue = "10",description = "allowed time difference between SLSTR and OLCI products")
    private long hoursCutoff;

    /*@Parameter(description = "The list of bands in the target product.", alias = "sourceBands", itemAlias = "band", rasterDataNodeType = Band.class)
    private String[] targetBandNames;
    The interface for band selection and default selection will be enabled for version-2 */


    @Override
    public void initialize() throws OperatorException {

        if (! isValidOlciProduct(olciSource)) {
            throw new OperatorException("OLCI product is not valid");
        }

        if (! isValidSlstrProduct(slstrSource)) {
            throw new OperatorException("SLSTR product is not valid");
        }

        /* Will be enabled/reworked along with the band selection in version-2
        if (targetBandNames == null) {
            throw new OperatorException("Zero bands for target product are chosen");
        }*/

        checkDate(slstrSource,olciSource);

        Product slstrInput = GPF.createProduct("Resample", getSlstrResampleParams(slstrSource), slstrSource);
        HashMap<String, Product> sourceProductMap = new HashMap<>();
        sourceProductMap.put("masterProduct", olciSource);
        sourceProductMap.put("slaveProduct", slstrInput);
        Product collocatedTarget = GPF.createProduct("Collocate", getCollocateParams(), sourceProductMap);

        reprojectedTarget = GPF.createProduct("Reproject",getReprojectParams(),collocatedTarget);
    }

    static Map<String, Object> getReprojectParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("resampling","Nearest");
        params.put("orthorectify", false);
        params.put("noDataValue", "NaN");
        params.put("includeTiePointGrids",true);
        params.put("addDeltaBands",false);
        params.put("crs","EPSG:4326");
        return params;
    }

    static Map<String, Object> getCollocateParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("targetProductType", "S3_L1C_SYN");
        params.put("renameMasterComponents", false);
        params.put("renameSlaveComponents", false);
        params.put("resamplingType", "NEAREST_NEIGHBOUR");
        return params;
    }

    private HashMap<String, Object> getSlstrResampleParams(Product toResample) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("targetWidth", toResample.getSceneRasterWidth());
        params.put("targetHeight", toResample.getSceneRasterHeight());
        params.put("upsampling", "Nearest");
        params.put("downsampling", "First");
        params.put("flagDownsampling", "First");
        params.put("resampleOnPyramidLevels", false);
        return params;
    }

    private void checkDate(Product slstrSource,Product olciSource) throws OperatorException {
        long slstrTime = slstrSource.getStartTime().getAsDate().getTime();
        long olciTime = olciSource.getEndTime().getAsDate().getTime();
        long diff = slstrTime - olciTime;
        long diffInHours =  (diff) / (1000 * 60 * 60 );
        if (diffInHours>hoursCutoff) {
            throw new OperatorException("The SLSTR and OLCI products differ more than allowed. Please check your input times");
        }
    }

    private boolean isValidOlciProduct(Product product) {
        return product.getProductType().contains("OL_1");
    }

    private boolean isValidSlstrProduct(Product product) {
        return product.getProductType().contains("SL_1");
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(L1cSynOp.class);
        }
    }

}