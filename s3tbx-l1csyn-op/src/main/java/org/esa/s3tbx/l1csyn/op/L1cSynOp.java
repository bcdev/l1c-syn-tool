package org.esa.s3tbx.l1csyn.op;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureSource;
import org.opengis.feature.simple.SimpleFeature;
import ucar.ma2.InvalidRangeException;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
@OperatorMetadata(alias = "L1CSYN",
        label = "L1C SYN Tool",
        authors = "Marco Peters, Roman Shevchuk",
        copyright = "Brockmann Consult GmbH",
        description = "Sentinel-3 OLCI/SLSTR L1C SYN Tool",
        category = "Optical/Pre-Processing",
        version = "1.0")
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
    private String upsamplingMethod;

    @Parameter(alias = "reprojectionCRS",
            label = "Reprojection CRS",
            description = "The CRS used for the reprojection. If set to None or left empty, no reprojection will be performed.",
            //valueSet = {"EPSG:4326", "EPSG:9108", "EPSG:9122"},
            defaultValue = "EPSG:4326"
    )
    private String reprojectionCRS;


    @Parameter(alias = "bandsOlci",
            label = "OLCI raster data",
            description = "group of OLCI bands in output",
            valueSet = {"All", "Oa.._radiance", "FWHM_band_.*", "lambda0_band_.*","solar_flux_band_.*","quality_flags_.*",
                    "atmospheric_temperature_profile_.*","TP_.*","horizontal_wind.*","total_.*","humidity","sea_level_pressure","O.*A","S.*A"},
            defaultValue = "All"
    )
    private String[] bandsOlci;

    @Parameter(alias = "bandsSlstr",
            label = "SLSTR raster data",
            description = "group of SLSTR bands in output",
            valueSet = {"All", "F._BT_.*", "S._BT_.*","S*._radiance_an",".*_an.*",".*_ao.*",".*_bn.*",".*_bo.*",".*_bn.*",".*_co.*",".*_cn.*",
                    ".*_tn.*",".*_tx.*"},
            defaultValue = "All"
    )
    private String[] bandsSlstr;

    /*@Parameter(alias = "tiePointSelection",
            label = "Tie_point selection",
            description = "which tie point should be written to SYN product",
            valueSet = {"All", "only OLCI", "only SLSTR", "None"},
            defaultValue = "All"
    )
    private String tiePointSelection;
    */

    @Parameter(label = "Shapefile", description = "Optional file which may be used for selecting subset. This has priority over WKT GeoRegion.")
    private File shapeFile;

    @Parameter(alias = "geoRegion", label = "WKT region",
            description = "The subset region in geographical coordinates using WKT-format,\n" +
                    "e.g. POLYGON((<lon1> <lat1>, <lon2> <lat2>, ..., <lon1> <lat1>))\n" +
                    "(make sure to quote the option due to spaces in <geometry>).\n" +
                    "If not given, the entire scene is used.")
    private String geoRegion;

    /* Commented for now, as it is not supported yet
    @Parameter(label = "MISRfile", description = "Optional MISR file which may be used for coregistration of OLCI and SLSTR products")
    private File misrFile;
    */

    @Override
    public void initialize() throws OperatorException {

        if (!isValidOlciProduct(olciSource)) {
            throw new OperatorException("OLCI product is not valid");
        }

        if (!isValidSlstrProduct(slstrSource)) {
            throw new OperatorException("SLSTR product is not valid");
        }

        //todo : decide if it can be made it optional. Pull request addressing SLSTR product type was added to SNAP.
        fixSlstrProductType();

        checkDate(slstrSource, olciSource);

        if (shapeFile != null) {
            geoRegion = readShapeFile(shapeFile);
        }

        Product collocatedTarget;

        File misrFile = null;  //temporary, while MISR parameter is commented
        if (misrFile != null) {
            String misrFormat = getMisrFormat(misrFile);
            try {
                SlstrMisrTransform misrTransform = new SlstrMisrTransform(olciSource, slstrSource, misrFile);
                TreeMap mapOlciSlstr = misrTransform.getSlstrOlciMap();
                HashMap<String, Product> misrSourceProductMap = new HashMap<>();
                misrSourceProductMap.put("olciSourceProduct", olciSource);
                misrSourceProductMap.put("slstrSourceProduct", slstrSource);
                HashMap<String, Object> misrParams = new HashMap<>();
                misrParams.put("pixelMap", mapOlciSlstr);
                collocatedTarget = GPF.createProduct("Misregister", misrParams, misrSourceProductMap);
            } catch (InvalidRangeException e1) {
                throw new OperatorException("Misregistration failed. InvalidRangeException");
            } catch (IOException e2) {
                throw new OperatorException("Misregistration failes. I/O Exception ");
            }

        } else {
            Product slstrInput = GPF.createProduct("Resample", getSlstrResampleParams(slstrSource, upsamplingMethod), slstrSource);
            HashMap<String, Product> sourceProductMap = new HashMap<>();
            sourceProductMap.put("masterProduct", olciSource);
            sourceProductMap.put("slaveProduct", slstrInput);
            collocatedTarget = GPF.createProduct("Collocate", getCollocateParams(), sourceProductMap);
        }

        if (reprojectionCRS != null && !reprojectionCRS.toLowerCase().equals("none") && !reprojectionCRS.equals("")) {
            l1cTarget = GPF.createProduct("Reproject", getReprojectParams(), collocatedTarget);
        } else {
            l1cTarget = collocatedTarget;
        }
        Map<String, ProductData.UTC> startEndDateMap = getStartEndDate(slstrSource, olciSource);
        ProductData.UTC startDate = startEndDateMap.get("startDate");
        ProductData.UTC endDate = startEndDateMap.get("endDate");

        if (geoRegion != null) {
            l1cTarget = GPF.createProduct("Subset", getSubsetParameters(geoRegion), l1cTarget);
        }
        //
        MetadataElement slstrMetadata = slstrSource.getMetadataRoot();
        //MetadataElement olciMetadata = olciSource.getMetadataRoot();
        slstrMetadata.setName("SLSTRmetadata");
        l1cTarget.getMetadataRoot().addElement(slstrMetadata);
        //l1cTarget.getMetadataRoot().addElement(olciMetadata);
        //
        l1cTarget.setStartTime(startDate);
        l1cTarget.setEndTime(endDate);
        l1cTarget.setName(getSynName(slstrSource, olciSource));

        /*if (!tiePointSelection.equals("All")) {
            updateTiePointGrids(l1cTarget, tiePointSelection);
        }*/
        if (!Arrays.stream(bandsSlstr).anyMatch("All"::equals)) {
            updateBands(slstrSource, l1cTarget, bandsSlstr);
        }
        if (!Arrays.stream(bandsOlci).anyMatch("All"::equals)) {
            updateBands(olciSource, l1cTarget, bandsOlci);
        }
    }


    private void updateBands(Product inputProduct, Product l1cTarget, String[] bandsList) {
        if (!Arrays.asList(bandsList).contains("All")) {
            Pattern pattern = Pattern.compile("\\b(" + String.join("|", bandsList) + ")\\b");
            String[] bandNames = inputProduct.getBandNames();
            String[] tiePointGridNames = inputProduct.getTiePointGridNames();


            String[] tiePointBandNames = (String[]) ArrayUtils.addAll(bandNames, tiePointGridNames);

            for (String bandName : tiePointBandNames) {
                Matcher matcher = pattern.matcher(bandName);
                if (!matcher.matches()) {
                    l1cTarget.removeBand(l1cTarget.getBand(bandName));
                }
            }
            String[] maskNames = inputProduct.getMaskGroup().getNodeNames();
            for (String maskName : maskNames) {
                Matcher matcher = pattern.matcher(maskName);
                if (!matcher.matches()) {
                    l1cTarget.getMaskGroup().remove(l1cTarget.getMaskGroup().get(maskName));
                }
            }
        }
    }

    private void updateTiePointGrids(Product l1cTarget, String tiePointSelection) {
        if (tiePointSelection.equals("only OLCI") || tiePointSelection.equals("None")) {
            for (TiePointGrid tiePointGrid : slstrSource.getTiePointGrids()) {
                l1cTarget.removeBand(l1cTarget.getBand(tiePointGrid.getName()));
            }
        }
        if (tiePointSelection.equals("only SLSTR") || tiePointSelection.equals("None")) {
            for (TiePointGrid tiePointGrid : olciSource.getTiePointGrids()) {
                l1cTarget.removeBand(l1cTarget.getBand(tiePointGrid.getName()));
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

    private Map<String, Object> getSubsetParameters(String geoRegion) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("geoRegion", geoRegion);
        params.put("copyMetadata", true);
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

    protected HashMap<String, Object> getSlstrResampleParams(Product toResample, String upsamplingMethod) {
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
        long diffInSeconds = diff / 1000L;
        if (diffInSeconds > allowedTimeDiff) {
            throw new OperatorException("The SLSTR and OLCI products differ more than" + String.format("%d", diffInSeconds) + ". Please check your input times");
        }
    }

    private static Map<String, ProductData.UTC> getStartEndDate(Product slstrSource, Product olciSource) {
        HashMap<String, ProductData.UTC> dateMap = new HashMap<>();
        String startDateString;
        String endDateString;
        ProductData.UTC startDateUTC;
        ProductData.UTC endDateUTC;

        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        final long slstrStartTime = slstrSource.getStartTime().getAsDate().getTime();
        final long olciStartTime = olciSource.getStartTime().getAsDate().getTime();
        if (slstrStartTime < olciStartTime) {
            startDateUTC = slstrSource.getStartTime();
        } else {
            startDateUTC = olciSource.getStartTime();
        }
        final long slstrEndTime = slstrSource.getStartTime().getAsDate().getTime();
        final long olciEndTime = olciSource.getStartTime().getAsDate().getTime();
        if (slstrEndTime > olciEndTime) {
            endDateUTC = slstrSource.getEndTime();
        } else {
            endDateUTC = olciSource.getEndTime();
        }
        dateMap.put("startDate", startDateUTC);
        dateMap.put("endDate", endDateUTC);
        return dateMap;
    }

    private void fixSlstrProductType() {
        String filePath = slstrSource.getFileLocation().toString();
        File slstrFile = new File(filePath);
        try {
            this.slstrSource = ProductIO.readProduct(slstrFile, "Sen3");
        } catch (IOException e2) {
            throw new OperatorException("Can not reopen SLSTR product.");
        }
    }

    public static String getSynName(Product slstrSource, Product olciSource) throws OperatorException {
        // pattern is MMM_SS_L_TTTTTT_yyyymmddThhmmss_YYYYMMDDTHHMMSS_yyyyMMDDTHHMMSS_<instance ID>_GGG_<class ID>.<extension>
        if (slstrSource == null || olciSource == null) {
            return "L1C";
        }

        String slstrName = slstrSource.getName();
        String olciName = olciSource.getName();

        if (slstrName.length() < 81 || olciName.length() < 81) {
            return "L1C";
        }

        StringBuilder synName = new StringBuilder();
        if (olciName.contains("S3A") && slstrName.contains("S3A")) {
            synName.append("S3A_SY_1_SYN____");
        } else if (olciName.contains("S3B") && slstrName.contains("S3B")) {
            synName.append("S3B_SY_1_SYN____");
        } else {
            synName.append("________________");
        }
        Map<String, ProductData.UTC> startEndDateMap = getStartEndDate(slstrSource, olciSource);
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
        Date startDateUTC = startEndDateMap.get("startDate").getAsDate();
        String dateStringStart = dateFormat.format(startDateUTC);
        Date endDateUTC = startEndDateMap.get("endDate").getAsDate();
        String dateStringEnd = dateFormat.format(endDateUTC);
        synName.append(dateStringStart);
        synName.append("_");
        synName.append(dateStringEnd);
        synName.append("_");

        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date date = new Date();
        String currentDate = dateFormat.format(date);
        synName.append(currentDate);
        synName.append("_");
        String instanceString = slstrSource.getName().substring(64, 81);
        synName.append(instanceString);
        synName.append("_");
        synName.append("LN2_");
        synName.append("O_NT_"); /// Operational, non-time-critical
        String slstrBaseline = "___"; //slstrSource.getName().substring(91,94); //todo: clarify if there should be any baseline
        synName.append(slstrBaseline);
        synName.append(".SEN3");
        return synName.toString();
    }

    private int readDetectorIndex(int x, int y) {
        String indexString = olciSource.getBand("detector_index").getPixelString(x, y);
        int pixelIndex = Integer.parseInt(indexString);
        return pixelIndex;
    }

    private String getMisrFormat(File misrFile) {
        String format = null;
        format = "new";
        return format;
    }

    private String readShapeFile(File shapeFile) {
        try {
            ArrayList<Polygon> polygons = new ArrayList<>();
            GeometryFactory factory = new GeometryFactory();
            ShapefileDataStore dataStore = new ShapefileDataStore(shapeFile.toURL());
            ContentFeatureSource featureSource = dataStore.getFeatureSource();
            ContentFeatureCollection featureCollection = featureSource.getFeatures();
            SimpleFeatureIterator iterator = featureCollection.features();
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                List attributes = feature.getAttributes();
                for (Object attribute : attributes) {
                    if (attribute != null) {
                        MultiPolygon multiPolygon = ((MultiPolygon) attribute);
                        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                            Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
                            polygons.add(polygon);
                        }
                    }
                }
            }
            Polygon[] polygonsArray = new Polygon[polygons.size()];
            polygonsArray = polygons.toArray(polygonsArray);
            MultiPolygon combined = new MultiPolygon(polygonsArray, factory);
            return combined.toString();
        } catch (IOException e) {
            throw new OperatorException("something is wrong with your shapefile");
        }
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