package org.esa.s3tbx.l1csyn.op;

import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
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
import org.esa.snap.core.util.StopWatch;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureSource;
import org.opengis.feature.simple.SimpleFeature;
import ucar.ma2.InvalidRangeException;

import java.io.*;
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
        version = "2.5")
public class L1cSynOp extends Operator {

    private final long allowedTimeDiff = 200L;
    //private File misrFile = null;
    //private boolean duplicate = false;
    //private boolean fullMisr = false;

    @SourceProduct(alias = "olciProduct", label = "OLCI Product", description = "OLCI source product")
    private Product olciSource;

    @SourceProduct(alias = "slstrProduct", label = "SLSTR Product", description = "SLSTR source product")
    private Product slstrSource;

    @TargetProduct(label = "L1C SYN Product", description = "L1C SYNERGY output product")
    private Product l1cTarget;

    @Parameter(alias = "stayOnOlciGrid",
            label = "Keep final project on OLCI image grid",
            description = "If this parameter is set to true, the final product will be projected on OLCI image grid.",
            defaultValue = "false"
    )
    private boolean stayOnOlciGrid;

    @Parameter(alias = "reprojectionCRS",
            label = "Reprojection CRS",
            description = "The CRS used for the reprojection. If set to None or left empty, no reprojection will be performed. If MISR file is specified this setting will be neglected.",
            //valueSet = {"EPSG:4326", "EPSG:9108", "EPSG:9122"},
            defaultValue = "EPSG:4326"
    )
    private String reprojectionCRS;

    @Parameter(alias = "upsampling",
            label = "Resampling upsampling method",
            description = "The method used for interpolation (upsampling to a finer resolution).",
            valueSet = {"Nearest", "Bilinear", "Bicubic",},
            defaultValue = "Nearest"
    )
    private String upsamplingMethod;

    @Parameter(alias = "bandsOlci",
            label = "OLCI raster data",
            description = "Predefined regular expressions for selection of OLCI bands in the output product. Multiple selection is possible.",
            valueSet = {"All", "Oa.._radiance", "FWHM_band_.*", "lambda0_band_.*", "solar_flux_band_.*", "quality_flags.*",
                    "atmospheric_temperature_profile_.*", "TP_.*", "horizontal_wind.*", "total_.*", "humidity", "sea_level_pressure", "O.*A", "S.*A"},
            defaultValue = "All"
    )
    private String[] bandsOlci;

    @Parameter(alias = "bandsSlstr",
            label = "SLSTR raster data",
            description = "Predefined regular expressions for selection of OLCI bands in the output product. Multiple selection is possible.",
            valueSet = {"All", "F._BT_.*", "S._BT_.*", "S*._radiance_an", ".*_an.*", ".*_ao.*", ".*_bn.*", ".*_bo.*", ".*_bn.*", ".*_co.*", ".*_cn.*", ".*_fn.*", ".*_fo.*",
                    ".*_tn.*", ".*_tx.*"},
            defaultValue = "All"
    )
    private String[] bandsSlstr;

    @Parameter(alias = "olciRegexp",
            label = "Regular expressions for OLCI",
            description = "Regular expressions (comma-separated) to set up selection of OLCI bands. " +
                    "It has priority over OLCI raster data selection. Will not be considered if empty",
            defaultValue = ""
    )
    private String olciRegexp;

    @Parameter(alias = "slstrRegexp",
            label = "Regular expressions for SLSTR",
            description = "Regular expressions (comma-separated) to set up selection of SLSTR bands. " +
                    "It has priority over SLSTR raster data selection. Will not be considered if empty",
            defaultValue = ""
    )
    private String slstrRegexp;

    @Parameter(label = "Shapefile", description = "Optional file which may be used for selecting subset. This has priority over WKT GeoRegion.")
    private File shapeFile;

    @Parameter(alias = "geoRegion", label = "WKT region",
            description = "The subset region in geographical coordinates using WKT-format,\n" +
                    "e.g. POLYGON((<lon1> <lat1>, <lon2> <lat2>, ..., <lon1> <lat1>))\n" +
                    "(make sure to quote the option due to spaces in <geometry>).\n" +
                    "If not given, the entire scene is used.")
    private String geoRegion;

    @Parameter(alias = "MISRFile", label = "MISRfile", description = "Optional MISR file which may be used for coregistration of OLCI and SLSTR products")
    private File misrFile;

    @Parameter(alias = "duplicate", label = "duplicate pixel using MISR", description = "If set to true, during MISR geocoding, empty pixels will be filled with duplicates.",
            defaultValue = "false")
    private boolean duplicate;

    @Parameter(alias = "fullMISR", label = "use MISR for each band separately", description = "If set to true, during MISR geocoding, every SLSTR band geocoding will be calculated separately.",
            defaultValue = "false")
    private boolean fullMisr;

    @Parameter(alias = "formatMISR", label = "describes if provided MISR product is in new format", description = "If set to true, it is assumed that MISR product has new format.",
            defaultValue = "true")
    private boolean formatMisr;

    @Parameter(alias = "numCam", label = "camera ID for tests", description = "For the tests of the intermediate cameras, use 0-4 range. If set to 5, provide full MISR image. This is test option only",
            defaultValue = "99")
    private int numCam;

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
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date startDate = startEndDateMap.get("startDate").getAsDate();
        String dateStringStart = dateFormat.format(startDate);
        Date endDate = startEndDateMap.get("endDate").getAsDate();
        String dateStringEnd = dateFormat.format(endDate);
        synName.append(dateStringStart);
        synName.append("_");
        synName.append(dateStringEnd);
        synName.append("_");

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

    @Override
    public void initialize() throws OperatorException {

        if (!isValidOlciProduct(olciSource)) {
            throw new OperatorException("OLCI product is not valid");
        }

        if (!isValidSlstrProduct(slstrSource)) {
            throw new OperatorException("SLSTR product is not valid");
        }

        checkDate(slstrSource, olciSource);

        if (shapeFile != null) {
            geoRegion = readShapeFile(shapeFile);
        }

        Product collocatedTarget;

        if (misrFile != null) {
            String misrFormat = getMisrFormat(misrFile);
            try {
                //SLSTR offset
                int SLSTROffset = getSLSLTROffset();
                System.out.println(SLSTROffset + " SLSTR offset found");
                if (misrFormat.equals("valid") && !fullMisr) {
                    SlstrMisrTransform misrTransformNadir = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S3", formatMisr, -SLSTROffset);
                    SlstrMisrTransform misrTransformOblique = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "ao", formatMisr, -SLSTROffset);
                    // TODO : revert after tests
                    //
                    Map<int[], int[]> mapNadirS3;
                    if (numCam >= 0 && numCam < 5) {
                        mapNadirS3 = misrTransformNadir.getSlstrOlciInstrumentMap(numCam);
                    } else if (numCam == 5) {
                        mapNadirS3 = misrTransformNadir.getSlstrOlciSingleCameraMap();
                    } else {
                        mapNadirS3 = misrTransformNadir.getSlstrOlciMap();
                    }
                    Map<int[], int[]> mapObliqueAo = misrTransformOblique.getSlstrOlciMap();

                    HashMap<String, Object> misrParams = getMisrParams(null, null, mapNadirS3, null, null, null, mapObliqueAo, null, null);
                    HashMap<String, Product> misrSourceProductMap = new HashMap<>();
                    misrSourceProductMap.put("olciSource", olciSource);
                    misrSourceProductMap.put("slstrSource", slstrSource);
                    collocatedTarget = GPF.createProduct("Misregister", misrParams, misrSourceProductMap);
                    // TODO: for test, instrument maps replace back later
                    /*TreeMap camMap0 = misrTransformNadir.getSlstrOlciSingleCameraMap(0);
                    TreeMap camMap1 = misrTransformNadir.getSlstrOlciSingleCameraMap(1);
                    TreeMap camMap2 = misrTransformNadir.getSlstrOlciSingleCameraMap(2);
                    TreeMap camMap3 = misrTransformNadir.getSlstrOlciSingleCameraMap(3);
                    TreeMap camMap4 = misrTransformNadir.getSlstrOlciSingleCameraMap(4);
                    System.out.println(camMap0.size()+"CAM0");
                    System.out.println(camMap1.size()+"CAM1");
                    System.out.println(camMap2.size()+"CAM2");
                    System.out.println(camMap3.size()+"CAM3");
                    System.out.println(camMap4.size()+"CAM4");*/
                } else if (misrFormat.equals("valid") && fullMisr) {
                    Map<int[], int[]> mapOlciSlstrS1 = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S1", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrS2 = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S2", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrS3 = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S3", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrS4 = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S4", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrS5 = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S5", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrS6 = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "S6", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrao = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "ao", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrbo = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "ao", formatMisr, -SLSTROffset).getSlstrOlciMap();
                    Map<int[], int[]> mapOlciSlstrco = new SlstrMisrTransform(olciSource, slstrSource, misrFile, "ao", formatMisr, -SLSTROffset).getSlstrOlciMap();

                    HashMap<String, Object> misrParams = getMisrParams(mapOlciSlstrS1, mapOlciSlstrS2, mapOlciSlstrS3, mapOlciSlstrS4, mapOlciSlstrS5, mapOlciSlstrS6, mapOlciSlstrao, mapOlciSlstrbo, mapOlciSlstrco);

                    HashMap<String, Product> misrSourceProductMap = new HashMap<>();
                    misrSourceProductMap.put("olciSource", olciSource);
                    misrSourceProductMap.put("slstrSource", slstrSource);

                    collocatedTarget = GPF.createProduct("Misregister", misrParams, misrSourceProductMap);
                } else {
                    throw new OperatorException("MISR file information is not read correctly");
                }
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
        if (reprojectionCRS != null && !reprojectionCRS.toLowerCase().equals("none") && !reprojectionCRS.equals("") && !stayOnOlciGrid && misrFile == null) {
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
        MetadataElement slstrMetadata = slstrSource.getMetadataRoot();
        slstrMetadata.setName("SLSTRmetadata");
        l1cTarget.getMetadataRoot().addElement(slstrMetadata);
        l1cTarget.setStartTime(startDate);
        l1cTarget.setEndTime(endDate);
        l1cTarget.setName(getSynName(slstrSource, olciSource));

        if (slstrRegexp == null || slstrRegexp.equals("")) {
            updateBands(slstrSource, l1cTarget, bandsSlstr);
        } else {
            updateBands(slstrSource, l1cTarget, readRegExp(slstrRegexp));
        }
        if (olciRegexp == null || olciRegexp.equals("")) {
            updateBands(olciSource, l1cTarget, bandsOlci);
        } else {
            updateBands(olciSource, l1cTarget, readRegExp(olciRegexp));
        }
        l1cTarget.setDescription("SENTINEL-3 SYN Level 1C Product");
    }

    private HashMap<String, Object> getMisrParams(Map<int[], int[]> S1PixelMap, Map<int[], int[]> S2PixelMap, Map<int[], int[]> S3PixelMap,
                                                  Map<int[], int[]> S4PixelMap, Map<int[], int[]> S5PixelMap, Map<int[], int[]> S6PixelMap,
                                                  Map<int[], int[]> aoPixelMap, Map<int[], int[]> boPixelMap, Map<int[], int[]> coPixelMap) {
        HashMap<String, Object> misrParams = new HashMap<>();
        misrParams.put("duplicate", duplicate);
        misrParams.put("singlePixelMap", !fullMisr);

        misrParams.put("S1PixelMap", MapToWrapedArrayFactory.createWrappedArray(S1PixelMap));
        misrParams.put("S2PixelMap", MapToWrapedArrayFactory.createWrappedArray(S2PixelMap));
        misrParams.put("S3PixelMap", MapToWrapedArrayFactory.createWrappedArray(S3PixelMap));
        misrParams.put("S4PixelMap", MapToWrapedArrayFactory.createWrappedArray(S4PixelMap));
        misrParams.put("S5PixelMap", MapToWrapedArrayFactory.createWrappedArray(S5PixelMap));
        misrParams.put("S6PixelMap", MapToWrapedArrayFactory.createWrappedArray(S6PixelMap));
        misrParams.put("aoPixelMap", MapToWrapedArrayFactory.createWrappedArray(aoPixelMap));
        misrParams.put("boPixelMap", MapToWrapedArrayFactory.createWrappedArray(boPixelMap));
        misrParams.put("coPixelMap", MapToWrapedArrayFactory.createWrappedArray(coPixelMap));

        return misrParams;
    }

    private String[] readRegExp(String regExp) {
        regExp = regExp.replace(" ", "");
        return regExp.split(",");
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
                    if (l1cTarget.getBand(bandName) != null) {
                        l1cTarget.removeBand(l1cTarget.getBand(bandName));
                    }
                    if (l1cTarget.getTiePointGrid(bandName) != null) {
                        l1cTarget.removeTiePointGrid(l1cTarget.getTiePointGrid(bandName));
                    }
                }
            }
            String[] maskNames = inputProduct.getMaskGroup().getNodeNames();
            for (String maskName : maskNames) {
                Matcher matcher = pattern.matcher(maskName);
                if (!matcher.matches()) {
                    if (l1cTarget.getMaskGroup().get(maskName) != null) {
                        l1cTarget.getMaskGroup().remove(l1cTarget.getMaskGroup().get(maskName));
                    }
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

    private void fixSlstrProductType() {
        //This method was used before SNAP v.6.0.9 in order to ensure that SLSTR product is opened in correct format.
        String filePath = slstrSource.getFileLocation().toString();
        File slstrFile = new File(filePath);
        try {
            this.slstrSource = ProductIO.readProduct(slstrFile, "Sen3");
        } catch (IOException e2) {
            throw new OperatorException("Can not reopen SLSTR product.");
        }
    }

    private String getMisrFormat(File misrFile) {
        String format = null;
        if (misrFile.getAbsolutePath().endsWith("xfdumanifest.xml")) {
            format = "valid";
        }
        if (format == null) {
            throw new OperatorException("Wrong MISR file format. Please specify path to xfdumanifest.xml of MISR product.");
        }
        return format;
    }

    // calculates offset between SLSTR and OLCI products
    private int getSLSLTROffset() throws IOException {
        final Band flagBand = olciSource.getBand("quality_flags");
        final int olciWidth = flagBand.getRasterWidth();
        final int[] flags = new int[olciWidth];
        flagBand.readPixels(0, 0, olciWidth, 1, flags);

        final Band olciLatBand = olciSource.getBand("latitude");
        final float[] olciLats = new float[olciWidth];
        olciLatBand.readPixels(0, 0, olciWidth, 1, olciLats);

        final Band slstrLatBand = slstrSource.getBand("latitude_an");
        final int slstrWidth = slstrLatBand.getRasterWidth();
        final float[] slstrLats = new float[slstrWidth];
        slstrLatBand.readPixels(0, 0, slstrWidth, 1, slstrLats);

        double lat = 0;
        for (int i = 0; i < olciWidth; i++) {
            if ((flags[i] & 0x2000000) != 0x2000000) {  // which is invalid
                lat = olciLats[i];
                break;
            }
        }

        for (int i = 1; i < slstrWidth; i++) {
            float prev = slstrLats[i-1];
            float current = slstrLats[i];
            if (prev >= lat && lat >= current) {
                System.out.println("done: offset = " + i);
                return i;
            }
        }

        return 0;
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
            throw new OperatorException("The provided shapefile could not be read", e);
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