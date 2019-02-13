package org.esa.s3tbx.l1csyn.op;

import com.vividsolutions.jts.geom.Geometry;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.converters.JtsGeometryConverter;
import org.esa.snap.core.util.converters.RectangleConverter;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
@OperatorMetadata(alias = "L1CSYN",
        //label = "L1C SYN Tool",
        authors = "Marco Peters, Roman Shevchuk",
        copyright = "Brockmann Consult GmbH",
        description = "Sentinel-3 OLCI/SLSTR L1C SYN Tool",
        category = "Optical/Pre-Processing",
        version= "1.0")
public class L1cSynOp extends Operator {

    @SourceProduct(alias = "olciProduct", label = "OLCI Product", description = "OLCI source product")
    private Product olciSource;

    @SourceProduct(alias = "slstrProduct", label = "SLSTR Product", description = "SLSTR source product")
    private Product slstrSource;

    @TargetProduct(label = "L1C SYN Product", description = "L1C SYNERGY output product")
    private Product l1cTarget;

    /*@Parameter(converter = JtsGeometryConverter.class,
            description = "The subset region in geographical coordinates using WKT-format,\n" +
                    "e.g. POLYGON((<lon1> <lat1>, <lon2> <lat2>, ..., <lon1> <lat1>))\n" +
                    "(make sure to quote the option due to spaces in <geometry>).\n" +
                    "If not given, the entire scene is used.")
    private Geometry geoRegion;

    @Parameter(converter = RectangleConverter.class,
            description = "The subset region in pixel coordinates.\n" +
                    "Use the following format: <x>,<y>,<width>,<height>\n" +
                    "If not given, the entire scene is used. The 'geoRegion' parameter has precedence over this parameter.")
    private Rectangle region = null;
    */

    @Parameter(label = "Allowed time difference", defaultValue = "10", unit = "h",
            description = "Allowed time difference between SLSTR and OLCI products")
    private long allowedTimeDiff;

    @Parameter(alias = "upsampling",
            label = "Resampling upsampling method",
            description = "The method used for interpolation (upsampling to a finer resolution).",
            valueSet = {"Nearest", "Bilinear", "Bicubic"},
            defaultValue = "Nearest"
    )
    private  String upsamplingMethod;

    @Parameter(alias="bandselection",
            label = "Group of bands selected",
            description = "Preselected group of bands",
            valueSet = {"All", "Radiance only", "Radiance and quality flags"},
            defaultValue = "All"
    )
    private String bandSelection;

    /*@Parameter(description = "The list of bands in the target product.", alias = "sourceBands", itemAlias = "band", rasterDataNodeType = Band.class)
    private String[] targetBandNames;
    The interface for band selection and default selection will be enabled for version-2 */


    @Override
    public void initialize() throws OperatorException {
        //setParameterDefaultValues();

        if (!isValidOlciProduct(olciSource)) {
            throw new OperatorException("OLCI product is not valid");
        }

        if (!isValidSlstrProduct(slstrSource)) {
            throw new OperatorException("SLSTR product is not valid");
        }

        /* Will be enabled/reworked along with the band selection in version-2
        if (targetBandNames == null) {
            throw new OperatorException("Zero bands for target product are chosen");
        }*/
        setSlstrBands(bandSelection, slstrSource);
        setOlciBands(bandSelection,olciSource);

        checkDate(slstrSource, olciSource);

        Product slstrInput = GPF.createProduct("Resample", getSlstrResampleParams(slstrSource,upsamplingMethod), slstrSource);
        HashMap<String, Product> sourceProductMap = new HashMap<>();

        sourceProductMap.put("masterProduct", olciSource);
        sourceProductMap.put("slaveProduct", slstrInput);
        Product collocatedTarget = GPF.createProduct("Collocate", getCollocateParams(), sourceProductMap);
        l1cTarget = GPF.createProduct("Reproject", getReprojectParams(), collocatedTarget);
    }

    protected void setSlstrBands(String bandSelection, Product slstrSource) {
        Band[] bands = slstrSource.getBands();
        if (bandSelection.equals("Radiance only")){
            for (Band band : bands) {
                if (! band.getName().contains("BT")) {
                    slstrSource.removeBand(band);
                }
            }
        }
    }

    protected void setOlciBands(String bandSelection, Product olciSource) {
        Band[] bands = olciSource.getBands();
        if (bandSelection.equals("Radiance only")){
            for (Band band : bands) {
                if (! band.getName().contains("radiance")) {
                    olciSource.removeBand(band);
                }
            }
        }
    }

    static Map<String, Object> getReprojectParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("resampling", "Nearest");
        params.put("orthorectify", false);
        params.put("noDataValue", "NaN");
        params.put("includeTiePointGrids", true);
        params.put("addDeltaBands", false);
        params.put("crs", "EPSG:4326");
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

    protected static HashMap<String, Object> getSlstrResampleParams(Product toResample, String upsamplingMethod) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("targetWidth", toResample.getSceneRasterWidth());
        params.put("targetHeight", toResample.getSceneRasterHeight());
        params.put("upsampling", upsamplingMethod);
        params.put("downsampling", "First");
        params.put("flagDownsampling", "First");
        params.put("resampleOnPyramidLevels", false);
        return params;
    }

    private void checkDate(Product slstrSource, Product olciSource) throws OperatorException {
        long slstrTime = slstrSource.getStartTime().getAsDate().getTime();
        long olciTime = olciSource.getEndTime().getAsDate().getTime();
        long diff = slstrTime - olciTime;
        long diffInHours = (diff) / (1000 * 60 * 60);
        if (diffInHours > allowedTimeDiff) {
            throw new OperatorException("The SLSTR and OLCI products differ more than" + String.format("%d", diffInHours) + ". Please check your input times");
        }
    }

    private boolean isValidOlciProduct(Product product) {
        return product.getProductType().contains("OL_1") || product.getName().contains("OLCI");
    }

    private boolean isValidSlstrProduct(Product product) {
        return product.getProductType().contains("SL_1") || product.getName().contains("SLSTR");
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(L1cSynOp.class);
        }
    }

}