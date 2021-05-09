package org.esa.s3tbx.l1csyn.op;

import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.dataio.geocoding.ComponentGeoCoding;
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
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.store.ContentFeatureCollection;
import org.geotools.data.store.ContentFeatureSource;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import ucar.ma2.InvalidRangeException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OperatorMetadata(alias = "L1CSYN",
        label = "L1C SYN Tool",
        authors = "Marco Peters, Roman Shevchuk",
        copyright = "Brockmann Consult GmbH",
        description = "Sentinel-3 OLCI/SLSTR L1C SYN Tool",
        category = "Optical/Pre-Processing",
        version = "2.6")
public class L1cSynOp extends Operator {

    private static final long ALLOWED_TIME_DIFF = 200L;
    private static final boolean USE_FULL_MISR = true;
    private static final boolean USE_ORPHAN = true;
    private static final boolean FILL_EMPTY_PIXELS = true;

    @SourceProduct(label = "OLCI Product", description = "OLCI L1 ERR or EFR source product")
    private Product olciProduct;

    @SourceProduct(label = "SLSTR Product", description = "SLSTR L1 RBT source product")
    private Product slstrProduct;

    @TargetProduct(label = "L1C SYN Product", description = "L1C SYNERGY output product")
    private Product l1cTarget;

    @Parameter(label = "Keep final product on OLCI image grid",
            description = "If this parameter is set to true, the final product will be kept in OLCI image grid.",
            defaultValue = "true"
    )
    private boolean stayOnOlciGrid;

    @Parameter(label = "Use MISR Product", description = "If set to false MISR product will not be used, instead products will collocated",
            defaultValue = "true")
    private boolean useMISR;

    @Parameter(label = "MISR Product", description = "Optional MISR file which may be used for co-registration of OLCI and SLSTR products")
    private File misrFile;

    @Parameter(label = "Reprojection CRS",
            description = "The CRS used for the reprojection. If set to None or left empty, no reprojection will be performed. If MISR file is specified this setting will be neglected.",
            defaultValue = "EPSG:4326"
    )
    private String reprojectionCRS;

    @Parameter(label = "Resampling upsampling method",
            description = "The method used for interpolation (upsampling to a finer resolution).",
            valueSet = {"Nearest", "Bilinear", "Bicubic",},
            defaultValue = "Nearest"
    )
    private String upsampling;

    @Parameter(label = "OLCI raster data",
            description = "Predefined regular expressions for selection of OLCI bands in the output product. Multiple selection is possible.",
            valueSet = {"All", "Oa.._radiance", "FWHM_band_.*", "lambda0_band_.*", "solar_flux_band_.*", "quality_flags.*",
                    "atmospheric_temperature_profile_.*", "TP_.*", "horizontal_wind.*", "total_.*", "humidity", "sea_level_pressure", "O.*A", "S.*A"},
            defaultValue = "All"
    )
    private String[] bandsOlci;

    @Parameter(label = "SLSTR raster data",
            description = "Predefined regular expressions for selection of OLCI bands in the output product. Multiple selection is possible.",
            valueSet = {"All", "F._BT_.*", "S._BT_.*", "S*._radiance_an", ".*_an.*", ".*_ao.*", ".*_bn.*", ".*_bo.*", ".*_bn.*", ".*_co.*", ".*_cn.*", ".*_fn.*", ".*_fo.*",
                    ".*_tn.*", ".*_tx.*"},
            defaultValue = "All"
    )
    private String[] bandsSlstr;

    @Parameter(label = "Regular expressions for OLCI",
            description = "Regular expressions (comma-separated) to set up selection of OLCI bands. " +
                    "It has priority over OLCI raster data selection. Will not be considered if empty"
    )
    private String olciRegexp;

    @Parameter(label = "Regular expressions for SLSTR",
            description = "Regular expressions (comma-separated) to set up selection of SLSTR bands. " +
                    "It has priority over SLSTR raster data selection. Will not be considered if empty"
    )
    private String slstrRegexp;

    @Parameter(label = "Shapefile", description = "Optional file which may be used for selecting subset. This has priority over WKT GeoRegion.")
    private File shapeFile;

    @Parameter(label = "WKT region",
            description = "The subset region in geographical coordinates using WKT-format,\n" +
                    "e.g. POLYGON((<lon1> <lat1>, <lon2> <lat2>, ..., <lon1> <lat1>))\n" +
                    "(make sure to quote the option due to spaces in <geometry>).\n" +
                    "If not given, the entire scene is used.")
    private String geoRegion;

    // This parameters are not used in production but are useful for fasten up the debugging and testing the code. As of Mar2021 they turned into variables
    //
    //@Parameter(label = "Fill empty pixels", description = "If set to true, empty pixels will be filled with neighbouring values.",
    //        defaultValue = "true")
    //private boolean fillEmptyPixels;
    //
    //@Parameter(alias = "orphan", label = "Orphan pixel using MISR", description = "If set to true, during MISR geocoding, orphan pixels will be used in addition.",
    //        defaultValue = "true")
    //private boolean orphan;
    //
    //@Parameter(label = "Use MISR for each band separately", description = "If set to true, mis-registration information for every SLSTR band will be calculated separately.",
    //        defaultValue = "true")
    //private boolean fullMisr;

    @Override
    public void initialize() throws OperatorException {
        if (!isValidOlciProduct(olciProduct)) {
            throw new OperatorException("OLCI product is not valid");
        }

        if (!isValidSlstrProduct(slstrProduct)) {
            throw new OperatorException("SLSTR product is not valid");
        }

        checkDate(slstrProduct, olciProduct);

        // todo: update validation of PixelGeoCoding
        checkGeocoding(slstrProduct, olciProduct);

        if (shapeFile != null) {
            geoRegion = readShapeFile(shapeFile);
        }

        Product collocatedTarget;

        if (useMISR) {
            ensureValidMISRFile(misrFile);
            try {
                HashMap<String, Object> misrParams = createMisrParamsMap();

                HashMap<String, Product> misrSourceProductMap = new HashMap<>();
                misrSourceProductMap.put("olciSource", olciProduct);
                misrSourceProductMap.put("slstrSource", slstrProduct);

                collocatedTarget = GPF.createProduct("Misregister", misrParams, misrSourceProductMap);
            } catch (InvalidRangeException e1) {
                throw new OperatorException("Misregistration failed. InvalidRangeException", e1);
            } catch (IOException e2) {
                throw new OperatorException("Misregistration failed. I/O Exception ", e2);
            }

        } else {
            Product slstrInput = GPF.createProduct("Resample", getSlstrResampleParams(slstrProduct, upsampling), slstrProduct);
            HashMap<String, Product> sourceProductMap = new HashMap<>();
            sourceProductMap.put("masterProduct", olciProduct);
            sourceProductMap.put("slaveProduct", slstrInput);
            collocatedTarget = GPF.createProduct("Collocate", getCollocateParams(), sourceProductMap);
        }

        if (reprojectionCRS != null && !reprojectionCRS.equalsIgnoreCase("none") && !reprojectionCRS.equals("") && !stayOnOlciGrid && misrFile == null) {
            l1cTarget = GPF.createProduct("Reproject", getReprojectParams(), collocatedTarget);
        } else {
            l1cTarget = collocatedTarget;
        }
        Map<String, ProductData.UTC> startEndDateMap = L1cSynUtils.getStartEndDate(slstrProduct, olciProduct);
        ProductData.UTC startDate = startEndDateMap.get("startDate");
        ProductData.UTC endDate = startEndDateMap.get("endDate");

        if (geoRegion != null) {
            l1cTarget = GPF.createProduct("Subset", getSubsetParameters(geoRegion), l1cTarget);
        }

        MetadataElement slstrMetadata = slstrProduct.getMetadataRoot();
        slstrMetadata.setName("SLSTRmetadata");
        l1cTarget.getMetadataRoot().addElement(slstrMetadata);
        l1cTarget.setStartTime(startDate);
        l1cTarget.setEndTime(endDate);
        l1cTarget.setName(L1cSynUtils.getSynName(slstrProduct, olciProduct));
        removeOrphanBands(l1cTarget);
        if (slstrRegexp == null || slstrRegexp.equals("")) {
            updateBands(slstrProduct, l1cTarget, bandsSlstr);
        } else {
            updateBands(slstrProduct, l1cTarget, readRegExp(slstrRegexp));
        }
        if (olciRegexp == null || olciRegexp.equals("")) {
            updateBands(olciProduct, l1cTarget, bandsOlci);
        } else {
            updateBands(olciProduct, l1cTarget, readRegExp(olciRegexp));
        }
        l1cTarget.setAutoGrouping(olciProduct.getAutoGrouping().toString() + slstrProduct.getAutoGrouping().toString());
        l1cTarget.setDescription("SENTINEL-3 SYN Level 1C Product");
    }

    private HashMap<String, Object> createMisrParamsMap() throws InvalidRangeException, IOException {

        // Better not using more then 2 threads. More caused issues
        int parallelThreads = 2;
        final ExecutorService executorService = Executors.newFixedThreadPool(parallelThreads);

        final HashMap<String, Map<int[], int[]>> misrMap = new HashMap<>();
        if (!USE_FULL_MISR) {
            executorService.submit(createMapGenerationRunnable(misrMap, "S3"));
            executorService.submit(createMapGenerationRunnable(misrMap, "ao"));
        } else {

            String[] slstrTargetBands;
            if (slstrRegexp == null || slstrRegexp.equals("")) {
                slstrTargetBands = getSlstrBands(slstrProduct, bandsSlstr);
            } else {
                slstrTargetBands = getSlstrBands(slstrProduct, readRegExp(slstrRegexp));
            }

            if (mapNeeded(slstrTargetBands, "S1")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "S1"));
            }

            if (mapNeeded(slstrTargetBands, "S2")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "S2"));
            }

            if (mapNeeded(slstrTargetBands, "S3")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "S3"));
            }

            if (mapNeeded(slstrTargetBands, "S4")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "S4"));
            }

            if (mapNeeded(slstrTargetBands, "S5")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "S5"));
            }

            if (mapNeeded(slstrTargetBands, "S6")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "S6"));
            }

            if (mapNeeded(slstrTargetBands, "ao")) {
                executorService.submit(createMapGenerationRunnable(misrMap, "ao"));
            }

        }
        executorService.shutdown();
        while (true) {
            try {
                if (executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                throw new OperatorException("Misregistration failed.", e);
            }
        }

        // currently we use the 'ao' data also for bo and co
        // if misr file changes in this respect han we need to adapt this here
        misrMap.put("boPixelMap", misrMap.get("aoPixelMap"));
        misrMap.put("coPixelMap", misrMap.get("aoPixelMap"));

        // currently we use the 'ao' data also for bo and co
        // if misr file changes in this respect han we need to adapt this here
        misrMap.put("boOrphanMap", misrMap.get("aoOrphanMap"));
        misrMap.put("coOrphanMap", misrMap.get("aoOrphanMap"));

        if (!USE_FULL_MISR) {
            misrMap.put("S1PixelMap", misrMap.get("S3PixelMap"));
            misrMap.put("S2PixelMap", misrMap.get("S3PixelMap"));
            misrMap.put("S4PixelMap", misrMap.get("S3PixelMap"));
            misrMap.put("S5PixelMap", misrMap.get("S3PixelMap"));
            misrMap.put("S6PixelMap", misrMap.get("S3PixelMap"));
            misrMap.put("aoPixelMap", misrMap.get("aoPixelMap"));
            misrMap.put("boPixelMap", misrMap.get("aoPixelMap"));
            misrMap.put("coPixelMap", misrMap.get("aoPixelMap"));
            misrMap.put("S1OrphanMap", misrMap.get("S3OrphanMap"));
            misrMap.put("S2OrphanMap", misrMap.get("S3OrphanMap"));
            misrMap.put("S4OrphanMap", misrMap.get("S3OrphanMap"));
            misrMap.put("S5OrphanMap", misrMap.get("S3OrphanMap"));
            misrMap.put("S6OrphanMap", misrMap.get("S3OrphanMap"));
            misrMap.put("aoOrphanMap", misrMap.get("aoOrphanMap"));
            misrMap.put("boOrphanMap", misrMap.get("aoOrphanMap"));
            misrMap.put("coOrphanMap", misrMap.get("aoOrphanMap"));
        }

        HashMap<String, Object> misrParams = new HashMap<>(misrMap);
        misrParams.put("fillEmptyPixels", FILL_EMPTY_PIXELS);
        misrParams.put("orphan", USE_ORPHAN);

        return misrParams;
    }


    private Runnable createMapGenerationRunnable(HashMap<String, Map<int[], int[]>> misrMap, final String bandType) {
        return () -> {
            try {
                setMapsAsParameter(misrMap, bandType);
            } catch (InvalidRangeException e1) {
                throw new OperatorException("Misregistration failed. InvalidRangeException", e1);
            } catch (IOException e2) {
                throw new OperatorException("Misregistration failed. I/O Exception ", e2);
            }
        };
    }

    private void setMapsAsParameter(HashMap<String, Map<int[], int[]>> misrMap, String bandType) throws InvalidRangeException, IOException {
        misrMap.put(bandType + "PixelMap", getPixelMap(bandType));
        misrMap.put(bandType + "OrphanMap", getOrphanMap(bandType));
    }

    private Map<int[], int[]> getOrphanMap(String bandType) throws InvalidRangeException, IOException {
        Map<int[], int[]> mapOrphanSlstr = new SlstrMisrTransform(olciProduct, slstrProduct, misrFile, bandType).getOrphanOlciMap();
        final Map<int[], int[]> s1Orphans = MapToWrapedArrayFactory.createWrappedArray(mapOrphanSlstr);
        mapOrphanSlstr.clear();
        return s1Orphans;
    }

    private Map<int[], int[]> getPixelMap(String bandType) throws InvalidRangeException, IOException {
        Map<int[], int[]> mapOlciSlstr = new SlstrMisrTransform(olciProduct, slstrProduct, misrFile, bandType).getSlstrOlciMap();
        final Map<int[], int[]> pixelMap = MapToWrapedArrayFactory.createWrappedArray(mapOlciSlstr);
        mapOlciSlstr.clear();
        return pixelMap;
    }

    private String[] readRegExp(String regExp) {
        regExp = regExp.replace(" ", "");
        return regExp.split(",");
    }

    private String[] getSlstrBands(Product inputProduct, String[] bandsList) {
        String[] bandNames = inputProduct.getBandNames();
        String[] tiePointGridNames = inputProduct.getTiePointGridNames();
        Object[] tiePointBandNames = ArrayUtils.addAll(bandNames, tiePointGridNames);
        if (!Arrays.asList(bandsList).contains("All")) {
            Pattern pattern = Pattern.compile("\\b(" + String.join("|", bandsList) + ")\\b");
            for (String bandName : (String[]) tiePointBandNames) {
                Matcher matcher = pattern.matcher(bandName);
                if (!matcher.matches()) {
                    tiePointBandNames = ArrayUtils.removeElement(tiePointBandNames, bandName);
                }
            }
            return (String[]) tiePointBandNames;
        } else {
            return (String[]) tiePointBandNames;
        }
    }

    private boolean mapNeeded(String[] bandsList, String key) {
        for (String bandName : bandsList) {
            if (bandName.contains(key)) {
                return true;
            }
        }
        return false;
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

    private void removeOrphanBands(Product l1cTarget) {
        for (Band band : l1cTarget.getBands()) {
            if (band.getName().contains("orphan")) {
                l1cTarget.removeBand(band);
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
        if (diffInSeconds > ALLOWED_TIME_DIFF) {
            throw new OperatorException("The SLSTR and OLCI products differ more than" + String.format("%d", diffInSeconds) + ". Please check input products");
        }
    }

    private void checkGeocoding(Product slstrSource, Product olciSource) {
        if (!(olciSource.getSceneGeoCoding() instanceof ComponentGeoCoding)) {
            throw new OperatorException("OLCI product geocoding is not set to pixel-based geo-coding. Please check your SNAP configuration");
        }
        if (!(slstrSource.getBand("S3_radiance_an").getGeoCoding() instanceof ComponentGeoCoding)) {
            throw new OperatorException("SLSTR product geocoding is not set to pixel-based geo-coding. Please check your SNAP configuration");
        }
    }

    private void ensureValidMISRFile(File misrFile) {
        if (misrFile == null) {
            throw new OperatorException("MISR file was not specified.");
        }
        if (!misrFile.getAbsolutePath().endsWith("xfdumanifest.xml")) {
            throw new OperatorException("Wrong MISR file format. Please specify path to xfdumanifest.xml of MISR product.");
        }
    }

    private String readShapeFile(File shapeFile) {
        try {
            ArrayList<Polygon> polygons = new ArrayList<>();
            GeometryFactory factory = new GeometryFactory();
            ShapefileDataStore dataStore = new ShapefileDataStore(shapeFile.toURI().toURL());
            ContentFeatureSource featureSource = dataStore.getFeatureSource();
            ContentFeatureCollection featureCollection = featureSource.getFeatures();
            SimpleFeatureIterator iterator = featureCollection.features();
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                List<Object> attributes = feature.getAttributes();
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


    //********************
    //  Below is the code which is not used
    // ********************

    @SuppressWarnings("unused")
    private void dumpMemoryUsage(String label) {
        long totalMem = Runtime.getRuntime().totalMemory();
        long usedMem = totalMem - Runtime.getRuntime().freeMemory();
        float mbFactor = 1.0F / (1024.0F * 1024.0F);
        System.out.printf("%s: Memory usage %.1f/%.1f (%.1f)%n", label,
                          usedMem * mbFactor, totalMem * mbFactor, Runtime.getRuntime().maxMemory() * mbFactor);
    }

    // calculates offset between SLSTR and OLCI products
    @SuppressWarnings("unused")
    private int getSLSLTROffset() throws IOException {
        final Band flagBand = olciProduct.getBand("quality_flags");
        final int olciWidth = flagBand.getRasterWidth();
        final int[] flags = new int[olciWidth];
        flagBand.readPixels(0, 0, olciWidth, 1, flags);

        final Band olciLatBand = olciProduct.getBand("latitude");
        final float[] olciLats = new float[olciWidth];
        olciLatBand.readPixels(0, 0, olciWidth, 1, olciLats);

        final Band slstrLatBand = slstrProduct.getBand("latitude_an");
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
            float prev = slstrLats[i - 1];
            float current = slstrLats[i];
            if (prev >= lat && lat >= current) {
                System.out.println("done: offset = " + i);
                return i;
            }
        }

        return 0;
    }

}