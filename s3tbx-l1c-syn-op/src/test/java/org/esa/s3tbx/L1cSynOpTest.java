package org.esa.s3tbx;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.junit.Test;


import java.io.IOException;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class L1cSynOpTest {



    @Test
    public void testL1cSynOpTest() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("SLSTRSub100.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("OLCISub100.nc").getFile();

        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setSourceProduct("olciProduct",olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct",slstrProduct);
        Product result = l1cSynOp.getTargetProduct();
        int numBands =  result.getNumBands();
        String productType = result.getProductType();
        Band oa03band = result.getBand("Oa03_radiance") ;
        int height = result.getSceneRasterHeight();
        int width = result.getSceneRasterWidth();

        assertEquals(42,height);
        assertEquals(60,width);
        assertEquals(414,numBands);
        assertEquals("S3_L1C_SYN",productType);
        assertNotNull(oa03band);
    }

    @Test
    public void testGetCollocateParams() {
        Map<String, Object> map = L1cSynOp.getCollocateParams();
        boolean renameMasterComponents = (boolean) map.get("renameMasterComponents");
        boolean renameSlaveComponents = (boolean) map.get("renameSlaveComponents");
        String resamplingType = (String) map.get("resamplingType");
        String targetProductType = (String) map.get("targetProductType");
        assertEquals( false,renameMasterComponents);
        assertEquals( false,renameSlaveComponents);
        assertEquals( "NEAREST_NEIGHBOUR",resamplingType);
        assertEquals( "S3_L1C_SYN",targetProductType);
    }

    @Test
    public void testGetReprojectParams() {
        Map<String, Object> map = L1cSynOp.getReprojectParams();
        String resampling = (String) map.get("resampling");
        boolean orthorectify = (boolean) map.get("orthorectify");
        String crs = (String) map.get("crs");
        assertEquals("Nearest",resampling);
        assertEquals(false,orthorectify);
        assertEquals("EPSG:4326",crs);

    }

    @Test
    public void testGetResampleParameters() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("SLSTRSub100.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);

        Map<String, Object> map = L1cSynOp.getSlstrResampleParams(slstrProduct);
        int width = (int) map.get("targetWidth");
        int height = (int) map.get("targetHeight");
        String upsampling = (String) map.get("upsampling");
        String downsampling = (String) map.get("downsampling");
        String flagDownsampling = (String) map.get("flagDownsampling");
        boolean resampleOnPyramidLevels = (boolean) map.get("resampleOnPyramidLevels");
        assertEquals(30,width);
        assertEquals(24,height);
        assertEquals("Nearest",upsampling);
        assertEquals("First",downsampling);
        assertEquals("First",flagDownsampling);
        assertEquals(false,resampleOnPyramidLevels);

    }

}