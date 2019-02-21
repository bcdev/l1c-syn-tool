package org.esa.s3tbx.l1csyn.op;

import org.esa.s3tbx.l1csyn.op.ui.L1cSynDialog;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductManager;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.ui.SourceProductSelector;
import org.esa.snap.core.util.PropertyMap;
import org.esa.snap.ui.AppContext;
import org.esa.snap.ui.DefaultAppContext;
import org.esa.snap.ui.product.ProductSceneView;
import org.junit.Test;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class L1cSynOpTest {


    @Test
    public void testL1cSynOpTest() throws IOException {
        String slstrFilePath = L1cSynOpTest.class.getResource("SLSTRSub100.nc").getFile();
        String olciFilePath = L1cSynOpTest.class.getResource("OLCISub100.nc").getFile();

        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        Product olciProduct = ProductIO.readProduct(olciFilePath);
        Operator l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();
        l1cSynOp.setSourceProduct("olciProduct", olciProduct);
        l1cSynOp.setSourceProduct("slstrProduct", slstrProduct);
        l1cSynOp.setParameter("upsampling","Nearest");
        Product result = l1cSynOp.getTargetProduct();
        int numBands = result.getNumBands();
        String productType = result.getProductType();
        Band oa03band = result.getBand("Oa03_radiance");
        int height = result.getSceneRasterHeight();
        int width = result.getSceneRasterWidth();

        assertEquals(42, height);
        assertEquals(60, width);
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
        String slstrFilePath = L1cSynOpTest.class.getResource("SLSTRSub100.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        String upsamplingMethod = "Nearest";
        Operator l1cSynOp = new L1cSynOp();
        Map<String, Object> map = ((L1cSynOp) l1cSynOp).getSlstrResampleParams(slstrProduct, upsamplingMethod);
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
    public void testOrderSourceProductSelector() throws IOException {
        final AppContext appContext = new DefaultAppContext("SNAP");
        L1cSynDialog dialog = new L1cSynDialog("L1CSYN", appContext, "L1c Synergy Tool", "L1cSynTool", "_L1cSyn");
        String slstrFilePath = L1cSynOpTest.class.getResource("SLSTRSub100.nc").getFile();
        Product slstrProduct = ProductIO.readProduct(slstrFilePath);
        dialog.setSourceProduct("SLSTR",slstrProduct);
        List<SourceProductSelector> sourceProductSelectorList = dialog.getSourceProductSelectorList();
        String slstrProductName = sourceProductSelectorList.get(1).getSelectedProduct().getName();
        assertEquals("SLSTRSub100",slstrProductName);
    }


}