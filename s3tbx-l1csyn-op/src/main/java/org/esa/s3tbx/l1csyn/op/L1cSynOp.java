package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

@SuppressWarnings("unused")
@OperatorMetadata(alias = "L1CSYN",
        label = "L1C SYN Tool",
        authors = "Marco Peters, Roman Shevchuk",
        copyright = "Brockmann Consult GmbH",
        description = "Sentinel-3 OLCI/SLSTR L1C SYN Tool",
        category = "Optical/Pre-Processing",
        version= "1.0")
public class L1cSynOp extends Operator {

    private long allowedTimeDiff = 200l;

    @SourceProduct(alias = "olciProduct", label = "OLCI Product", description = "OLCI source product")
    private Product olciSource;

    @SourceProduct(alias = "slstrProduct", label = "SLSTR Product", description = "SLSTR source product")
    private Product slstrSource;

    @TargetProduct(label = "L1C SYN Product", description = "L1C SYNERGY output product")
    private Product l1cTarget;

    @Parameter(alias = "upsampling",
            label = "Resampling upsampling method",
            description = "The method used for interpolation (upsampling to a finer resolution).",
            valueSet = {"Nearest", "Bilinear", "Bicubic"},
            defaultValue = "Nearest"
    )
    private  String upsamplingMethod;

    @Parameter(alias = "reprojectionCRS",
            label = "Reprojection crs",
            description = "T",
            valueSet = {"EPSG:4326", "EPSG:9108", "EPSG:9122"},
            defaultValue = "EPSG:4326"
    )
    private String reprojectionCRS;


    @Parameter(alias = "bandsOlci",
            label = "bands OLCI",
            description = "group of OLCI bands in output",
            valueSet = {"All","Oa*_radiance","FWHM","lambda"},
            defaultValue = "All"
    )
    private String bandsOlci;

    @Parameter(alias = "bandsSlstr",
            label = "bands SLSTR",
            description = "group of SLSTR bands in output",
            valueSet = {"All","F*BT","S*BT"},
            defaultValue = "All"
    )
    private String bandsSlstr;

    @Override
    public void initialize() throws OperatorException {

        if (!isValidOlciProduct(olciSource)) {
            throw new OperatorException("OLCI product is not valid");
        }

        if (!isValidSlstrProduct(slstrSource)) {
            throw new OperatorException("SLSTR product is not valid");
        }

        checkDate(slstrSource,olciSource);
        updateSlstrBands(slstrSource, bandsSlstr);
        updateOlciBands(olciSource, bandsOlci);

        Product slstrInput = GPF.createProduct("Resample", getSlstrResampleParams(slstrSource,upsamplingMethod), slstrSource);
        HashMap<String, Product> sourceProductMap = new HashMap<>();

        sourceProductMap.put("masterProduct", olciSource);
        sourceProductMap.put("slaveProduct", slstrInput);
        Product collocatedTarget = GPF.createProduct("Collocate", getCollocateParams(), sourceProductMap);
        l1cTarget = GPF.createProduct("Reproject", getReprojectParams(), collocatedTarget);
    }

    private void updateOlciBands(Product olciSource, String bandsOlci){
        if (bandsOlci.equals("All")) {
            return;
        }
        else if (bandsOlci.equals("Oa*_radiance")){
            for (Band band : olciSource.getBands()){
                if (!band.getName().matches("Oa.._radiance")){
                    olciSource.removeBand(band);
                }
            }
        }
    }

    private void updateSlstrBands(Product slstrSource, String bandsSlstr){
        if (bandsSlstr.equals("All")){
            return;
        }
        else if (bandsSlstr.equals("F*BT")){
            for (Band band : slstrSource.getBands()){
                if (!band.getName().matches("F._BT\\S+")){
                    slstrSource.removeBand(band);
                }
            }
        }
        else if (bandsSlstr.equals("S*BT")){
            for (Band band : slstrSource.getBands()){
                if (!band.getName().matches("S._BT\\S+")){
                    slstrSource.removeBand(band);
                }
            }
        }
    }

     Map<String, Object> getReprojectParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("resampling", "Nearest");
        params.put("orthorectify", false);
        params.put("noDataValue", "NaN");
        params.put("includeTiePointGrids", true);
        params.put("addDeltaBands", false);
        params.put("crs", reprojectionCRS);
        return params;
    }

    protected Map<String, Object> getCollocateParams() {
        HashMap<String, Object> params = new HashMap<>();
        params.put("targetProductType", "S3_L1C_SYN");
        params.put("renameMasterComponents", false);
        params.put("renameSlaveComponents", false);
        params.put("resamplingType", "NEAREST_NEIGHBOUR");
        return params;
    }

    protected  HashMap<String, Object> getSlstrResampleParams(Product toResample, String upsamplingMethod) {
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
        long diffInSeconds = diff / 1000L ;
        if (diffInSeconds > allowedTimeDiff) {
            throw new OperatorException("The SLSTR and OLCI products differ more than" + String.format("%d", diffInSeconds) + ". Please check your input times");
        }
    }

    private static Map<String, String> getStartEndDate(Product slstrSource, Product olciSource)  {
        HashMap<String, String> dateMap = new HashMap<>();
        String startDate;
        String endDate;
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        final long slstrStartTime = slstrSource.getStartTime().getAsDate().getTime();
        final long olciStartTime = olciSource.getStartTime().getAsDate().getTime();
        if (slstrStartTime < olciStartTime){
           startDate = dateFormat.format(slstrSource.getStartTime().getAsDate());
        }
        else {
            startDate = dateFormat.format(olciSource.getStartTime().getAsDate());
        }
        final long slstrEndTime = slstrSource.getStartTime().getAsDate().getTime();
        final long olciEndTime = olciSource.getStartTime().getAsDate().getTime();
        if (slstrEndTime > olciEndTime){
            endDate = dateFormat.format(slstrSource.getEndTime().getAsDate());
        }
        else {
            endDate = dateFormat.format(olciSource.getEndTime().getAsDate());
        }

        dateMap.put("startDate",startDate);
        dateMap.put("endDate",endDate);
        return  dateMap;
    }


    public static String getSynName(Product slstrSource, Product olciSource) throws OperatorException {
        // pattern is MMM_SS_L_TTTTTT_yyyymmddThhmmss_YYYYMMDDTHHMMSS_yyyyMMDDTHHMMSS_<instance ID>_GGG_<class ID>.<extension>
        if (slstrSource==null || olciSource==null ){
            return "L1C";
        }

        String slstrName = slstrSource.getName();
        String olciName = olciSource.getName();

        StringBuilder synName = new StringBuilder();
        if (olciName.contains("S3A") && slstrName.contains("S3A")){
            synName.append( "S3A_SY_1_SYN____");
        }
        else if (olciName.contains("S3B") && slstrName.contains("S3B")){
            synName.append( "S3B_SY_1_SYN____");
        }
        else {
            throw new OperatorException("The SLSTR and OLCI products are from different missions");
        }
        /*String startTimeEndTime = slstrSource.getName().substring(16,47);
        synName.append(startTimeEndTime);
        synName.append("_");*/
        Map<String,String> startEndDateMap = getStartEndDate(slstrSource,olciSource);
        String startDate = startEndDateMap.get("startDate");
        String endDate = startEndDateMap.get("endDate");
        synName.append(startDate);
        synName.append("_");
        synName.append(endDate);
        synName.append("_");

        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = new Date();
        String currentDate = dateFormat.format(date);
        synName.append(currentDate);
        synName.append("_");
        String instanceString = slstrSource.getName().substring(64,81);
        synName.append(instanceString);
        synName.append("_");
        synName.append("LN2_");
        synName.append("O_NT_"); /// Operational, non-time-critical
        String slstrBaseline = "___"; //slstrSource.getName().substring(91,94);
        synName.append(slstrBaseline);
        synName.append(".SEN3");
        return synName.toString();
    }


    private boolean isValidOlciProduct(Product product) {
        return product.getProductType().contains("OL_1") || product.getName().contains("OL_1");
    }

    private boolean isValidSlstrProduct(Product product) {
        return product.getProductType().contains("SL_1") || product.getName().contains("SL_1");
    }

    public static class Spi extends OperatorSpi {
        public Spi() {
            super(L1cSynOp.class);
        }
    }

}