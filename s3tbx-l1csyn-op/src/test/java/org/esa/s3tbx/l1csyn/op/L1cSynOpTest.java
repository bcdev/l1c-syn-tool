package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

public class L1cSynOpTest {


    @Test
    public void testL1cSynOpTest() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();

        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        Product result = l1cSynOp.getTargetProduct();
        int numBands = result.getNumBands();
        String productType = result.getProductType();
        Band oa03band = result.getBand("Oa03_radiance");
        int height = result.getSceneRasterHeight();
        int width = result.getSceneRasterWidth();

        assertEquals(42, height);
        assertEquals(50, width);
        assertEquals(414, numBands);
        assertEquals("S3_L1C_SYN", productType);
        assertNotNull(oa03band);
    }

    @Test
    public void testGetCollocateParams() {
        Operator l1cSynOp = new L1cSynOp();
        Map<String, Object> map = ((L1cSynOp) l1cSynOp).getCollocateParams();
        boolean renameMasterComponents = (boolean) map.get("renameMasterComponents");
        boolean renameSlaveComponents = (boolean) map.get("renameSlaveComponents");
        String resamplingType = (String) map.get("resamplingType");
        String targetProductType = (String) map.get("targetProductType");
        assertFalse(renameMasterComponents);
        assertFalse(renameSlaveComponents);
        assertEquals("NEAREST_NEIGHBOUR", resamplingType);
        assertEquals("S3_L1C_SYN", targetProductType);
    }

    @Test
    public void testGetReprojectParams() {
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        Map<String, Object> map = ((L1cSynOp) l1cSynOp).getReprojectParams();
        String resampling = (String) map.get("resampling");
        boolean orthorectify = (boolean) map.get("orthorectify");
        String crs = (String) map.get("crs");
        assertEquals("Nearest", resampling);
        assertFalse(orthorectify);
        assertEquals("EPSG:4326", crs);

    }

    @Test
    public void testGetResampleParameters() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        Map<String, Object> map = ((L1cSynOp) l1cSynOp).getSlstrResampleParams(slstrProduct,"Nearest");
        int width = (int) map.get("targetWidth");
        int height = (int) map.get("targetHeight");
        String upsampling = (String) map.get("upsampling");
        String downsampling = (String) map.get("downsampling");
        String flagDownsampling = (String) map.get("flagDownsampling");
        boolean resampleOnPyramidLevels = (boolean) map.get("resampleOnPyramidLevels");
        assertEquals(30, width);
        assertEquals(24, height);
        assertEquals("Nearest", upsampling);
        assertEquals("First", downsampling);
        assertEquals("First", flagDownsampling);
        assertFalse(resampleOnPyramidLevels);

    }

    @Test
    public  void testBandSelection() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();

        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        String[] bandsOlci = {"Oa.._radiance", "FWHM_band_.*", "solar_flux_band_.*", "quality_flags.*",
                "atmospheric_temperature_profile_.*", "TP_.*"};
        String[] bandsSlstr = {".*_an.*", ".*_ao.*"};
        l1cSynOp.setParameter("bandsOlci", bandsOlci);
        l1cSynOp.setParameter("bandsSlstr",bandsSlstr);
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        Product result = l1cSynOp.getTargetProduct();
        assertTrue(result.containsBand("S1_radiance_an"));
        assertTrue(result.containsBand("S3_radiance_ao"));
        assertTrue(result.containsBand("Oa03_radiance"));
        assertTrue(result.containsBand("FWHM_band_12"));
        assertFalse(result.containsBand("S4_radiance_bo"));
        assertFalse(result.containsBand("S5_radiance_cn"));
        assertFalse(result.containsBand("lambda0_band_5"));
    }

    @Test
    public void testDefaultNameCreation() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();

        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        Product result = l1cSynOp.getTargetProduct();
        assertTrue(result.getName().startsWith("S3A_SY_1_SYN____20170313T110342_20170313T110643"));
        assertTrue(result.getName().endsWith("0179_015_208_2520_LN2_O_NT____.SEN3"));
    }

    @Test
    public void testBandsRegExps() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        l1cSynOp.setParameter("olciRegexp","Oa.._radiance, lambda0_band_.*");
        l1cSynOp.setParameter("slstrRegexp","S*._radiance_an, .*_an.*");
        Product result = l1cSynOp.getTargetProduct();
        assertTrue(result.containsBand("Oa01_radiance"));
        assertTrue(result.containsBand("Oa11_radiance"));
        assertTrue(result.containsBand("lambda0_band_3"));
        assertTrue(result.containsBand("bayes_an"));
        assertTrue(result.containsBand("S3_radiance_an"));
        assertFalse(result.containsBand("bayes_bn"));
        assertFalse(result.containsBand("solar_flux_band_4"));
        assertEquals(69,result.getNumBands());
    }

    @Test
    public void testWKTRegion() throws  IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        l1cSynOp.setParameterDefaultValues();
        l1cSynOp.setParameter("geoRegion","POLYGON((-16.936 26.715, -15.507  26.715 , -15.507 21.844 , -16.936     21.844 ,-16.936 26.715))");
        Product result = l1cSynOp.getTargetProduct();
        assertEquals(5,result.getSceneRasterWidth());
        assertEquals(17,result.getSceneRasterHeight());
    }

    @Test
    public void testNoReprojection() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        l1cSynOp.setParameterDefaultValues();
        l1cSynOp.setParameter("stayOnOlciGrid",true);
        Product result = l1cSynOp.getTargetProduct();
        assertEquals("WGS84(DD)",result.getSceneGeoCoding().getMapCRS().getName().toString());
        assertEquals("Geodetic 2D",result.getSceneGeoCoding().getMapCRS().getCoordinateSystem().getName().toString());
        assertEquals(41,result.getSceneRasterHeight());
        assertEquals(49,result.getSceneRasterWidth());
    }

    @Test
    public void testMetadata() throws  IOException{
        String slstrFilePath = L1cSynOpTest.class.getResource("S3A_SL_1_RBT____20170313T110343_20170313T110643_20170314T172757_0179_015_208_2520_LN2_O_NT_002.SEN3.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("S3A_OL_1_EFR____20170313T110342_20170313T110642_20170314T162839_0179_015_208_2520_LN1_O_NT_002.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        l1cSynOp.setParameterDefaultValues();
        Product result = l1cSynOp.getTargetProduct();
        assertEquals(4,result.getMetadataRoot().getNumElements());
        assertEquals(0,result.getMetadataRoot().getNumAttributes());
        assertEquals(5,result.getMetadataRoot().getElement("SLSTRmetadata").getElement("Global_Attributes").getNumAttributes());
    }
}