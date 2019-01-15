package org.esa.s3tbx;

import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.junit.Test;


import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class L1cSynOpTest {

    static final String RASTER_NAME_QUALITY_FLAGS = "quality_flags";
    static final String RASTER_NAME_SEA_LEVEL_PRESSURE = "sea_level_pressure";
    static final String RASTER_NAME_TOTAL_OZONE = "total_ozone";
    static final String RASTER_NAME_SUN_ZENITH = "SZA";
    static final String RASTER_NAME_SUN_AZIMUTH = "SAA";
    static final String RASTER_NAME_VIEWING_ZENITH = "OZA";
    static final String RASTER_NAME_VIEWING_AZIMUTH = "OAA";
    static final String RASTER_NAME_ALTITUDE = "altitude";

    // OLCI sources
    static final int BAND_COUNT = 21;


    @Test
    public void testL1cSynOp() {
        Operator L1cSyn = new L1cSynOp();
        Product olciProduct = createOlciTestProduct();
        Product slstrProduct = createSlstrTestProduct();
        L1cSyn.setSourceProduct("OLCI Product",olciProduct);
        L1cSyn.setSourceProduct("SLSTR Product",slstrProduct);

    }

    @Test
    public void testgetCollocateParams() {
        Map<String, Object> map = L1cSynOp.getCollocateParams();
        boolean renameMasterComponents = (boolean) map.get("renameMasterComponents");
        boolean renameSlaveComponents = (boolean) map.get("renameSlaveComponents");
        String resamplingType = (String) map.get("resamplingType");
        String targetProductType = (String) map.get("targetProductType");
        assertEquals(renameMasterComponents, false);
        assertEquals(renameSlaveComponents, false);
        assertEquals(resamplingType, "NEAREST_NEIGHBOUR");
        assertEquals(targetProductType, "S3_L1C_SYN");
    }

    @Test
    public void testgetReprojectParams() {
        Map<String, Object> map = L1cSynOp.getReprojectParams();
        String resampling = (String) map.get("resampling");
        boolean orthorectify = (boolean) map.get("orthorectify");
        String crs = (String) map.get("crs");
        assertEquals(resampling,"Nearest");
        assertEquals(orthorectify,false);
        assertEquals(crs,"EPSG:4326");

    }



    private Product createOlciTestProduct()  {
        Product product = new Product("test-olci", "OL_1", 1, 1);
        for (int i = 1; i <= BAND_COUNT; i++) {
            String expression = String.valueOf(i);
            product.addBand(String.format("Oa%02d_radiance", i), expression);
            product.addBand("solar_flux_band_" + i, expression);
        }
        Date time = new Date();
        product.setStartTime(ProductData.UTC.create(time, 0));
        product.setEndTime(ProductData.UTC.create(time, 500));
        product.addBand(RASTER_NAME_ALTITUDE, "500");
        product.addBand(RASTER_NAME_SUN_AZIMUTH, "42");
        product.addBand(RASTER_NAME_SUN_ZENITH, "42");
        product.addBand(RASTER_NAME_VIEWING_AZIMUTH, "42");
        product.addBand(RASTER_NAME_VIEWING_ZENITH, "42");
        product.addBand(RASTER_NAME_SEA_LEVEL_PRESSURE, "999");
        product.addBand(RASTER_NAME_TOTAL_OZONE, "0.004");
        Band flagBand = product.addBand(RASTER_NAME_QUALITY_FLAGS, ProductData.TYPE_INT8);
        FlagCoding l1FlagsCoding = new FlagCoding(RASTER_NAME_QUALITY_FLAGS);
        product.getFlagCodingGroup().add(l1FlagsCoding);
        flagBand.setSampleCoding(l1FlagsCoding);
        return product;
    }

    private Product createSlstrTestProduct()  {
        Product product = new Product("test-slstr", "SL_1", 1, 1);
        for (int i = 1; i <= BAND_COUNT; i++) {
            String expression = String.valueOf(i);
            product.addBand(String.format("Oa%02d_radiance", i), expression);
            product.addBand("solar_flux_band_" + i, expression);
        }
        return product;
    }
}