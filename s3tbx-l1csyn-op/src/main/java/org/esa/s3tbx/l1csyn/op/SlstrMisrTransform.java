package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import ucar.ma2.*;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SlstrMisrTransform implements Serializable {
    private Product olciImageProduct;
    private final Product slstrImageProduct;
    private final String misrPath;
    private final String bandType;
    private final int olciNumRows;
    private final int olciNumCols;
    private final int slstrNumRows;
    private final int slstrNumCols;
    private boolean newTransform = false;
    private int minScan = 9999999;
    private int SLSTRoffset;

    private int N_DET_CAM = 740;

    SlstrMisrTransform(Product olciImageProduct, Product slstrImageProduct, File misrManifest, String bandType, boolean newTransform, int SLSTRoffset) {
        this.olciImageProduct = olciImageProduct;
        this.slstrImageProduct = slstrImageProduct;
        this.misrPath = misrManifest.getParent();
        this.bandType = bandType;
        this.olciNumRows = olciImageProduct.getBand("Oa17_radiance").getRasterHeight();
        this.olciNumCols = olciImageProduct.getBand("Oa17_radiance").getRasterWidth();
        if (bandType.contains("S")) {
            this.slstrNumRows = slstrImageProduct.getBand("S3_radiance_an").getRasterHeight();
            this.slstrNumCols = slstrImageProduct.getBand("S3_radiance_an").getRasterWidth();
        } else {
            this.slstrNumRows = slstrImageProduct.getBand("S3_radiance_ao").getRasterHeight();
            this.slstrNumCols = slstrImageProduct.getBand("S3_radiance_ao").getRasterWidth();
        }
        this.newTransform = newTransform;
        this.SLSTRoffset = SLSTRoffset;
    }

    // package access for testing only tb 2020-07-17
    static int[] getColRow(int scan, int pixel, int detector) {
        //todo : clarify the formula
        return new int[]{pixel, scan * 4 + detector};
    }

    //step 1 for orphan pixel
    private TreeMap<int[], int[]> getSlstrOrphanImageMap() throws IOException {
        TreeMap<int[], int[]> orphanMap = new TreeMap<>(new ComparatorIntArray());

        String path = slstrImageProduct.getFileLocation().getParent();
        String indexFilePath = path + "/indices_" + "an" + ".nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(indexFilePath);
        Variable scanVariable = netcdfFile.findVariable("scan_orphan_" + "an");
        Variable pixelVariable = netcdfFile.findVariable("pixel_orphan_" + "an");
        Variable detectorVariable = netcdfFile.findVariable("detector_orphan_" + "an");

        ArrayShort.D2 scanArray = (ArrayShort.D2) scanVariable.read();
        ArrayShort.D2 pixelArray = (ArrayShort.D2) pixelVariable.read();
        ArrayByte.D2 detectorArray = (ArrayByte.D2) detectorVariable.read();
        int orphanPixelsLenght = netcdfFile.findDimension("orphan_pixels").getLength();
        int rowLenght = netcdfFile.findDimension("rows").getLength();

        for (int i = 0; i < orphanPixelsLenght; i++) {
            for (int j = 0; j < rowLenght; j++) {
                short scan = scanArray.get(j, i);
                short pixel = pixelArray.get(j, i);
                byte detector = detectorArray.get(j, i);
                int[] gridPosition = {scan, pixel, detector};
                if (scan != -1 && pixel != -1 && detector != -1) {
                    int[] imagePosition = {i, j};
                    orphanMap.put(imagePosition, gridPosition);
                    if (scan < minScan) {
                        this.minScan = scan;
                    }
                }
            }
        }
        return orphanMap;
    }


    /// step 1 updated juni 2020, reupdated ~1july 2020
    private TreeMap<int[], int[]> getSlstrImageMap(int x, int y) throws IOException, InvalidRangeException {
        // Provides mapping between SLSTR image grid(x,y) and SLSTR instrument grid(scan,pixel,detector)
        //x and y are dimensions of SLSTR L1B raster
        TreeMap<int[], int[]> slstrMap = new TreeMap<>(new ComparatorIntArray());

        String path = slstrImageProduct.getFileLocation().getParent();
        String indexFilePath = path + "/indices_" + "an" + ".nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(indexFilePath);
        Variable scanVariable = netcdfFile.findVariable("scan_" + "an");
        Variable pixelVariable = netcdfFile.findVariable("pixel_" + "an");
        Variable detectorVariable = netcdfFile.findVariable("detector_" + "an");

        ArrayShort.D2 scanArray = (ArrayShort.D2) scanVariable.read();
        ArrayShort.D2 pixelArray = (ArrayShort.D2) pixelVariable.read();
        ArrayByte.D2 detectorArray = (ArrayByte.D2) detectorVariable.read();
        //add offset?
        //Variable offsetScanVariable = netcdfFile.findVariable("l0_scan_offset_an");
        //int scanOffset = offsetScanVariable.readScalarInt();

        //
        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                short scan = scanArray.get(j, i);
                short pixel = pixelArray.get(j, i);
                byte detector = detectorArray.get(j, i);
                if (scan != -1 && pixel != -1 && detector != -1) {
                    int[] imagePosition = {i - SLSTRoffset, j};
                    int[] gridPosition = {scan, pixel, detector};
                    slstrMap.put(imagePosition, gridPosition);
                    if (scan < minScan) {
                        this.minScan = scan;
                    }
                }
            }
        }
        return slstrMap;
    }


    //step2
    private Map<int[], int[]> getSlstrGridMisrMap(Map<int[], int[]> mapSlstr, boolean minimize) throws IOException {
        int SminOk = 0;
        //provides map between SLSTR instrument grid (scan,pixel,detector) and MISR file (row,col)
        TreeMap<int[], int[]> gridMap = new TreeMap<>(new ComparatorIntArray());
        //test block to rescale col-row
        if (minimize) {
            SminOk = 4 * this.minScan - 4;
            System.out.println(SminOk + " is the offset");
        }

        for (int[] scanPixelDetector : mapSlstr.values()) {
            int scan = scanPixelDetector[0];
            int pixel = scanPixelDetector[1];
            int detector = scanPixelDetector[2];
            int[] colRow = getColRow(scan, pixel, detector);
            if (minimize) {
                //TODO : check if only row should be normalized
                colRow[0] = colRow[0];
                colRow[1] = colRow[1] - SminOk;

            }
            gridMap.put(scanPixelDetector, colRow);
        }
        return gridMap;
    }


    // Step 3
    private Map<int[], int[]> getMisrOlciMap() throws IOException, InvalidRangeException {
        // provides mapping between SLSTR (row/col) and OLCI instrument grid (N_LINE_OLC/N_DET_CAM/N_CAM) from MISR product
        String bandName = "/misregist_Oref_" + bandType + ".nc";

        String misrBandFile = this.misrPath + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        int nLineOlcLength = netcdfFile.findDimension("N_LINE_OLC").getLength();
        int nDetCamLength = netcdfFile.findDimension("N_DET_CAM").getLength();
        int nCamLength = netcdfFile.findDimension("N_CAM").getLength();
        String rowVariableName = getRowVariableName(netcdfFile,"row_corresp_\\S+");
        String colVariableName = getColVariableName(netcdfFile,"col_corresp_\\S+");
        Variable rowVariable = netcdfFile.findVariable(rowVariableName);
        Variable colVariable = netcdfFile.findVariable(colVariableName);
        Variable rowOffsetVariable = netcdfFile.findVariable("input_products_row_offset");
        //int rowOffset = rowOffsetVariable.readScalarInt();
        int rowOffset = 0;
        int colOffset = 0;
        double colScale = 0;
        double rowScale = 0;
        rowOffset = rowVariable.findAttribute("add_offset").getNumericValue().intValue();
        colOffset = colVariable.findAttribute("add_offset").getNumericValue().intValue();
        colScale = colVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        rowScale = rowVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        TreeMap<int[], int[]> colRowMap = new TreeMap<>(new ComparatorIntArray());
        if (nLineOlcLength < 10000) {
            ArrayInt.D3 rowArray = (ArrayInt.D3) rowVariable.read();
            ArrayInt.D3 colArray = (ArrayInt.D3) colVariable.read();
            int col;
            int row;


            for (int i = 0; i < nCamLength; i++) {
                for (int j = 0; j < nLineOlcLength; j++) {
                    for (int k = 0; k < nDetCamLength; k++) {
                        // Type of variable of (row,col) might change with change of MISR format. Be careful here.
                        row = (int) (rowArray.get(i, j, k) * rowScale + rowOffset);
                        col = (int) (colArray.get(i, j, k) * colScale + colOffset);
                        if (col >= 0 && row >= 0) {
                            //if (row < slstrNumRows && col < slstrNumCols) {
                                int[] colRowArray = {col, row};
                                int[] position = {i, j, k};
                                colRowMap.put(colRowArray, position);
                            //}
                        }
                    }
                }
            }
        } else {
            int cut = 5000;
            for (int longDimSplitter = 10000; longDimSplitter < nLineOlcLength + 10000; longDimSplitter += 10000) {
                int step = 10000;
                if (longDimSplitter > nLineOlcLength) {
                    step = nLineOlcLength + step - longDimSplitter;
                    longDimSplitter = nLineOlcLength;
                }

                ArrayInt.D3 rowArray = (ArrayInt.D3) rowVariable.read(new int[]{0, longDimSplitter - step, 0}, new int[]{nCamLength, step, nDetCamLength});
                ArrayInt.D3 colArray = (ArrayInt.D3) colVariable.read(new int[]{0, longDimSplitter - step, 0}, new int[]{nCamLength, step, nDetCamLength});
                int col;
                int row;
                for (int i = 0; i < nCamLength; i++) {
                    for (int j = 0; j < step; j++) {
                        for (int k = 0; k < nDetCamLength; k++) {
                            // Type of variable of (row,col) might change with change of MISR format. Be careful here.
                            row = rowArray.get(i, j, k) + rowOffset;
                            col = colArray.get(i, j, k);
                            if (col >= 0 && row >= 0) {
                                //if (row < slstrNumRows && col < slstrNumCols) {
                                    int[] colRowArray = {col, row};
                                    int[] position = {i, j, k};
                                    colRowMap.put(colRowArray, position);
                                //}
                            }
                        }
                    }
                }
            }
        }
        netcdfFile.close();
        return colRowMap;
    }

    //step 3 for orphan pixels
    private TreeMap<int[], int[]> getMisrOlciOrphanMap() throws IOException, InvalidRangeException {
        // provides mapping between MISR (row/orphan) and OLCI instrument grid (N_LINE_OLC/N_DET_CAM/N_CAM) from MISR product
        String bandName = "/misregist_Oref_" + bandType + ".nc";
        String misrBandFile = this.misrPath + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        int nLineOlcLength = netcdfFile.findDimension("N_LINE_OLC").getLength();
        int nDetCamLength = netcdfFile.findDimension("N_DET_CAM").getLength();
        int nCamLength = netcdfFile.findDimension("N_CAM").getLength();
        String rowVariableName = getRowVariableName(netcdfFile,"row_corresp_\\S+");
        String orphanVariableName = getOrphanVariableName(netcdfFile);
        Variable rowVariable = netcdfFile.findVariable(rowVariableName);
        Variable orphanVariable = netcdfFile.findVariable(orphanVariableName);
        ArrayInt.D3 rowArray = (ArrayInt.D3) rowVariable.read();
        ArrayShort.D3 orphanArray = (ArrayShort.D3) orphanVariable.read();

        short orphan = -1;
        int row = -1;
        int rowOffset = 0;
        double rowScale = 0;
        TreeMap<int[], int[]> orphanRowMap = new TreeMap<>(new ComparatorIntArray());

        boolean match1 = orphanVariableName.matches("L1b_orphan_.._" + "a.");
        boolean match2 = orphanVariableName.matches("orphan_corresp_s._" + "a.");
        rowOffset = rowVariable.findAttribute("add_offset").getNumericValue().intValue();
        rowScale = rowVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        for (int i = 0; i < nCamLength; i++) {
            for (int j = 0; j < nLineOlcLength; j++) {
                for (int k = 0; k < nDetCamLength; k++) {
                    if (match1) {
                        row = (int) (rowArray.get(i, j, k) * rowScale + rowOffset);
                        orphan = orphanArray.get(i, j, k);
                    } else if (match2) {
                        row = (int) (rowArray.get(i, j, k) * rowScale + rowOffset);
                        orphan = orphanArray.get(i, j, k);
                    }
                    if (orphan > 0 && row > 0) {
                        int[] orphanRowArray = {orphan, row};
                        int[] position = {i, j, k};
                        orphanRowMap.put(orphanRowArray, position);
                    }
                }
            }
        }
        netcdfFile.close();
        return orphanRowMap;
    }

    // Step 4.2
    private Map<int[], int[]> getOlciMisrMap() throws IOException, InvalidRangeException {
        //should provide mapping between OLCI image grid and instrument grid
        int OLCIOffset = 0;
        TreeMap<int[], int[]> olciMap = new TreeMap<>(new ComparatorIntArray());
        String bandName = "/misreg_Oref_Oa17.nc";
        String misrBandFile = this.misrPath + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        String rowVariableName = "L1b_row_17";
        String colVariableName = "L1b_col_17";
        Variable rowVariable = netcdfFile.findVariable(rowVariableName);
        Variable colVariable = netcdfFile.findVariable(colVariableName);
        //
        if (rowVariable == null) {
            rowVariableName = "delta_row_17";
            rowVariable = netcdfFile.findVariable(rowVariableName);

        }
        if (colVariable == null) {
            colVariableName = "delta_col_17";
            colVariable = netcdfFile.findVariable(colVariableName);
        }
        //

        int nCamLength = netcdfFile.findDimension("N_CAM").getLength();
        int nLineOlcLength = netcdfFile.findDimension("N_LINE_OLC").getLength();
        int nDetCamLength = netcdfFile.findDimension("N_DET_CAM").getLength();

        Variable rowOffsetVariable = netcdfFile.findVariable("input_products_row_offset");
        int rowOffset = (int) rowOffsetVariable.readScalarInt();
        rowOffset = 0;
        if (nLineOlcLength < 10000) {
            ArrayShort.D3 rowArray = (ArrayShort.D3) rowVariable.read();
            ArrayShort.D3 colArray = (ArrayShort.D3) colVariable.read();
            for (int i = 0; i < nCamLength; i++) {
                for (int j = 0; j < nLineOlcLength; j++) {
                    for (int k = 0; k < nDetCamLength; k++) {
                        short row = rowArray.get(i, j, k);
                        short col = colArray.get(i, j, k);
                        int rowNorm = row + rowOffset;
                        if (rowNorm >= 0 && col >= 0) {
                            if (rowNorm < olciNumRows && col < olciNumCols) {
                                int[] gridCoors = {i, j, k};
                                int[] imageCoors = {col - OLCIOffset, rowNorm};
                                olciMap.put(gridCoors, imageCoors);
                            }
                        }
                    }
                }
            }
        } else {
            for (int longDimSplitter = 10000; longDimSplitter < nLineOlcLength + 10000; longDimSplitter += 10000) {
                int step = 10000;
                if (longDimSplitter > nLineOlcLength) {
                    step = nLineOlcLength + step - longDimSplitter;
                    longDimSplitter = nLineOlcLength;
                }
                ArrayShort.D3 rowArray = (ArrayShort.D3) rowVariable.read(new int[]{0, longDimSplitter - step, 0}, new int[]{nCamLength, step, nDetCamLength});
                ArrayShort.D3 colArray = (ArrayShort.D3) colVariable.read(new int[]{0, longDimSplitter - step, 0}, new int[]{nCamLength, step, nDetCamLength});
                for (int i = 0; i < nCamLength; i++) {
                    for (int j = 0; j < step; j++) {
                        for (int k = 0; k < nDetCamLength; k++) {
                            short row = rowArray.get(i, j, k);
                            short col = colArray.get(i, j, k);
                            int rowNorm = row + rowOffset;
                            if ((rowNorm) >= 0 && col >= 0) {
                                if ((rowNorm) < olciNumRows && col < olciNumCols) {
                                    int[] gridCoors = {i, j, k};
                                    int[] imageCoors = {col - OLCIOffset, rowNorm};
                                    olciMap.put(gridCoors, imageCoors);
                                }
                            }
                        }
                    }
                }
            }
        }
        netcdfFile.close();
        return olciMap;
    }

    TreeMap<int[], int[]> getOrphanOlciMap() throws  InvalidRangeException, IOException {
        //Provides mapping between orphan SLSTR pixels and OLCI image grid
        TreeMap<int[], int[]> gridMapOrphan = new TreeMap<>(new ComparatorIntArray());

        if (newTransform) {
            Map<int[], int[]> slstrOrphanMap = getSlstrOrphanImageMap(); // 1
            Map<int[], int[]> slstrOrphanMisrMap = getSlstrGridMisrMap(slstrOrphanMap, true); // 2
            Map<int[], int[]> misrOrphanOlciMap = getMisrOlciOrphanMap(); // 3
            Map<int[], int[]> olciImageOrphanMap = getOlciMisrMap(); // 4

            for (Map.Entry<int[], int[]> entry : slstrOrphanMap.entrySet()) {
                int[] slstrScanPixDet = entry.getValue();
                int[] rowOrphan = (int[]) slstrOrphanMisrMap.get(slstrScanPixDet);
                int[] mjk = misrOrphanOlciMap.get(rowOrphan);
                if (mjk != null) {
                    int[] xy = olciImageOrphanMap.get(mjk);
                    if (xy != null) {
                        gridMapOrphan.put(xy, entry.getKey());
                    }
                }
            }
        }
        TreeMap<int[], int[]> gridMap = new TreeMap<>(new ComparatorIntArray());
        gridMap.putAll(gridMapOrphan);
        return gridMap;
    }

    TreeMap<int[], int[]> getSlstrOlciMap() throws InvalidRangeException, IOException {
        //Provides mapping between SLSTR image grid and OLCI image grid
        TreeMap<int[], int[]> gridMapPixel = new TreeMap<>(new ComparatorIntArray());

        // New version
        if (newTransform) {

            Map<int[], int[]> slstrImageMap = getSlstrImageMap(slstrImageProduct.getSceneRasterWidth(), slstrImageProduct.getSceneRasterHeight()); //1
            Map<int[], int[]> slstrMisrMap = getSlstrGridMisrMap(slstrImageMap, true); //2
            Map<int[], int[]> misrOlciMap = getMisrOlciMap(); //3
            Map<int[], int[]> olciImageMap = getOlciMisrMap(); // 4
            for (Map.Entry<int[], int[]> entry : slstrImageMap.entrySet()) {
                int[] slstrScanPixDet = entry.getValue();
                int[] rowCol = slstrMisrMap.get(slstrScanPixDet);
                int[] mjk = misrOlciMap.get(rowCol);
                if (mjk != null) {
                    int[] xy = olciImageMap.get(mjk);
                    if (xy != null) {
                        gridMapPixel.put(xy, entry.getKey());
                    }
                }
            }

            TreeMap<int[], int[]> gridMap = new TreeMap<>(new ComparatorIntArray());
            gridMap.putAll(gridMapPixel);
            return gridMap;
        } else {
            // Old version of MISR transformation
            Map<int[], int[]> misrOlciMap = getMisrOlciMap(); //3
            Map<int[], int[]> olciImageMap = MapToWrapedArrayFactory.createWrappedArray(getOlciMisrMap()); // 4.2
            for (Map.Entry<int[], int[]> entry : misrOlciMap.entrySet()) {
                int[] mjk = entry.getValue();
                int[] xy = (int[]) olciImageMap.get(mjk);
                if (xy != null) {
                    gridMapPixel.put(xy, entry.getKey());
                }
            }
        }
        return gridMapPixel;
    }

    Map<int[], int[]> getSlstrOlciInstrumentMap(int camIndex) throws InvalidRangeException, IOException {
        Map<int[], int[]> gridMapPixel = new TreeMap<>(new ComparatorIntArray());
        Map<int[], int[]> slstrImageMap = getSlstrImageMap(slstrImageProduct.getSceneRasterWidth(), slstrImageProduct.getSceneRasterHeight()); //1
        Map<int[], int[]> slstrMisrMap = MapToWrapedArrayFactory.createWrappedArray(getSlstrGridMisrMap(slstrImageMap, true)); //2
        Map<int[], int[]> misrOlciMap = MapToWrapedArrayFactory.createWrappedArray(getMisrOlciMap()); //3

        for (Map.Entry<int[], int[]> entry : slstrImageMap.entrySet()) {
            int[] slstrScanPixDet = entry.getValue();
            int[] rowCol = (int[]) slstrMisrMap.get(slstrScanPixDet);
            int[] mjk = (int[]) misrOlciMap.get(rowCol);
            if (mjk != null) {
                if (mjk[0] == camIndex) {
                    int[] camCoors = new int[]{mjk[2], mjk[1]};
                    gridMapPixel.put(camCoors, entry.getKey());
                }
            }
        }
        return gridMapPixel;
    }

    Map<int[], int[]> getSlstrOlciSingleCameraMap() throws InvalidRangeException, IOException {
        Map<int[], int[]> gridMapPixel = new TreeMap<>(new ComparatorIntArray());
        Map<int[], int[]> slstrImageMap = getSlstrImageMap(slstrImageProduct.getSceneRasterWidth(), slstrImageProduct.getSceneRasterHeight()); //1
        Map<int[], int[]> slstrMisrMap = getSlstrGridMisrMap(slstrImageMap, true); //2
        for (Map.Entry<int[], int[]> entry : slstrImageMap.entrySet()) {
            int[] slstrScanPixDet = entry.getValue();
            int[] rowCol = (int[]) slstrMisrMap.get(slstrScanPixDet);
            gridMapPixel.put(rowCol, entry.getKey());
        }
        return gridMapPixel;
    }

    private String getRowVariableName(NetcdfFile netcdfFile, String pattern) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if ( variable.getName().matches(pattern) ) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Row variable not found");
    }

    private String getColVariableName(NetcdfFile netcdfFile, String pattern) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getFullName().matches(pattern)){
                return variable.getFullName();
            }
            /*if ( variable.getName().matches("col_corresp_s." + "_an") || variable.getName().matches("L2b_col_" + "..")) {
                return variable.getName();
            }
            else if ( variable.getName().matches("L1b_col_.._" + "..")) {
                return variable.getName();
            }*/
        }
        throw new NullPointerException("Col variable not found");
    }

    private String getOrphanVariableName(NetcdfFile netcdfFile) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getName().matches("L1b_orphan_.._" + "a.") || variable.getName().matches("orphan_corresp_s._" + "a.") || variable.getName().matches("L1b_orphan_" + "a.")) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Orphan variable not found");
    }

    //so far this value is not used, but may be needed in the future
    private int getSlstrS3Offset(Product slstrImageProduct) throws IOException {
        String path = slstrImageProduct.getFileLocation().getParent();
        String filePath = path + "/S3_radiance_an.nc";
        NetcdfFile netcdfFile = NetcdfFile.open(filePath);
        Number offsetAttribute = netcdfFile.findGlobalAttribute("start_offset").getNumericValue();
        System.out.println(offsetAttribute);
        System.out.println(filePath);
        int offsetValue = (int) offsetAttribute;
        netcdfFile.close();
        return offsetValue;
    }

    public static class ComparatorIntArray implements java.util.Comparator<int[]>, Serializable {
        @Override
        public int compare(int[] left, int[] right) {
            int comparedLength = Integer.compare(left.length, right.length);
            if (comparedLength == 0) {
                for (int i = 0; i < left.length; i++) {
                    int comparedValue = Integer.compare(left[i], right[i]);
                    if (comparedValue != 0) {
                        return comparedValue;
                    }
                }
                return 0;
            } else {
                return comparedLength;
            }
        }
    }
}
