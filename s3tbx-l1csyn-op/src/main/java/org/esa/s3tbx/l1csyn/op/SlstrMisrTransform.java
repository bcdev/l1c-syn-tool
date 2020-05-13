package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import ucar.ma2.*;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SlstrMisrTransform implements Serializable{
    private Product olciImageProduct;
    private Product slstrImageProduct;
    private String misrPath;
    private String bandType;
    private int olciNumRows;
    private int olciNumCols;
    private int slstrNumRows;
    private int slstrNumCols;
    private boolean newTransform = false;

    private int N_DET_CAM = 740;

    SlstrMisrTransform(Product olciImageProduct, Product slstrImageProduct, File misrManifest, String bandType, boolean newTransform) {
        this.olciImageProduct = olciImageProduct;
        this.slstrImageProduct = slstrImageProduct;
        this.misrPath = misrManifest.getParent();
        this.bandType = bandType;
        this.olciNumRows = olciImageProduct.getBand("Oa17_radiance").getRasterHeight();
        this.olciNumCols = olciImageProduct.getBand("Oa17_radiance").getRasterWidth();
        if (bandType.contains("S")){
            this.slstrNumRows = slstrImageProduct.getBand("S3_radiance_an").getRasterHeight();
            this.slstrNumCols = slstrImageProduct.getBand("S3_radiance_an").getRasterWidth();
        }
        else {
            this.slstrNumRows = slstrImageProduct.getBand("S3_radiance_ao").getRasterHeight();
            this.slstrNumCols = slstrImageProduct.getBand("S3_radiance_ao").getRasterWidth();
        }
        this.newTransform = newTransform;
    }

    private int[] getColRow(int scan, int pixel, int detector) {
        //todo : clarify the formula
        int row = scan * 4 + detector;
        int col = pixel;
        int[] colRow = new int[2];
        colRow[0] = col;
        colRow[1] = row;
        return colRow;
    }

    //step 1 for orphan pixel
    private TreeMap getSlstrOrphanImageMap() throws IOException{
        TreeMap<int[], int[]> orphanMap = new TreeMap<>(new ComparatorIntArray());

        String path = slstrImageProduct.getFileLocation().getParent();
        String indexFilePath = path + "/indices_"+"an"+".nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(indexFilePath);
        Variable scanVariable = netcdfFile.findVariable("scan_orphan_"+"an");
        Variable pixelVariable = netcdfFile.findVariable("pixel_orphan_"+"an");
        Variable detectorVariable = netcdfFile.findVariable("detector_orphan_"+"an");

        Array scanArray = scanVariable.read();
        Array pixelArray = pixelVariable.read();
        Array detectorArray = detectorVariable.read();
        int orphanPixelsLenght = netcdfFile.findDimension("orphan_pixels").getLength();
        int rowLenght = netcdfFile.findDimension("rows").getLength();

        for (int i = 0; i < orphanPixelsLenght; i++) {
            for (int j = 0; j < rowLenght; j++) {
                int[] imagePosition = {i,j};
                short scan =  ((ArrayShort.D2) scanArray).get(j,i);
                short pixel = ((ArrayShort.D2) pixelArray).get(j,i);
                byte detector = ((ArrayByte.D2) detectorArray).get(j,i);
                int[] gridPosition = {scan, pixel, detector};
                if (scan != -1 && pixel != -1 && detector != -1) {
                    orphanMap.put(imagePosition, gridPosition);
                }
            }
        }

        return orphanMap;
    }



    private TreeMap getSlstrImageMap(int x, int y) throws IOException, InvalidRangeException {
        // Provides mapping between SLSTR image grid(x,y) and SLSTR instrument grid(scan,pixel,detector)
        //x and y are dimensions of SLSTR L1B raster
        TreeMap<int[], int[]> slstrMap = new TreeMap<>(new ComparatorIntArray());

        String path = slstrImageProduct.getFileLocation().getParent();
        String indexFilePath = path + "/indices_"+"an"+".nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(indexFilePath);
        Variable scanVariable = netcdfFile.findVariable("scan_"+"an");
        Variable pixelVariable = netcdfFile.findVariable("pixel_"+"an");
        Variable detectorVariable = netcdfFile.findVariable("detector_"+"an");

        Array scanArray = scanVariable.read();
        Array pixelArray = pixelVariable.read();
        Array detectorArray = detectorVariable.read();

        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                int[] imagePosition = {i,j};
                short scan =  ((ArrayShort.D2) scanArray).get(j,i);
                short pixel = ((ArrayShort.D2) pixelArray).get(j,i);
                byte detector = ((ArrayByte.D2) detectorArray).get(j,i);
                int[] gridPosition = {scan, pixel, detector};
                if (scan != -1 && pixel != -1 && detector != -1) {
                    slstrMap.put(imagePosition, gridPosition);
                }
            }
        }
        return slstrMap;
    }



    private TreeMap getSlstrGridMisrMap(Map mapSlstr, boolean minimize) {
        //provides map between SLSTR instrument grid (scan,pixel,detector) and MISR file (row,col)
        TreeMap<int[], int[]> gridMap = new TreeMap<>(new ComparatorIntArray());
        //test block to rescale col-row
        int minRow = 99999999;
        int minCol = 99999999;
        if (minimize) {
            for (Object value : mapSlstr.values()) {
                int[] scanPixelDetector = (int[]) value;
                int scan = scanPixelDetector[0];
                int pixel = scanPixelDetector[1];
                int detector = scanPixelDetector[2];
                int[] colRow = getColRow(scan, pixel, detector);
                if (colRow[0] < minCol) {
                    minCol = colRow[0];
                }
                if (colRow[1] < minRow) {
                    minRow = colRow[1];
                }
            }
        }

        for (Object value : mapSlstr.values()) {
            int[] scanPixelDetector = (int[]) value;
            int scan = scanPixelDetector[0];
            int pixel = scanPixelDetector[1];
            int detector = scanPixelDetector[2];
            int[] colRow = getColRow(scan, pixel, detector);
            if (minimize) {
                colRow[0] = colRow[0] - minCol;
                colRow[1] = colRow[1] - minRow;
            }
            gridMap.put(scanPixelDetector, colRow);
        }
        return gridMap;
    }


    // Step 3
    private TreeMap<int[], int[]> getMisrOlciMap() throws IOException, InvalidRangeException {
        // provides mapping between SLSTR (row/col) and OLCI instrument grid (N_LINE_OLC/N_DET_CAM/N_CAM) from MISR product
        String bandName = "/misregist_Oref_"+bandType+".nc";

        String path = this.misrPath;
        String misrBandFile = path + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        int nLineOlcLength = netcdfFile.findDimension("N_LINE_OLC").getLength();
        int nDetCamLength = netcdfFile.findDimension("N_DET_CAM").getLength();
        int nCamLength = netcdfFile.findDimension("N_CAM").getLength();
        String rowVariableName = getRowVariableName(netcdfFile);
        String colVariableName = getColVariableName(netcdfFile);
        Variable rowVariable = netcdfFile.findVariable(rowVariableName);
        Variable colVariable = netcdfFile.findVariable(colVariableName);
        TreeMap<int[], int[]> colRowMap = new TreeMap<>(new ComparatorIntArray());
        if (nLineOlcLength < 10000) {
            Array rowArray = rowVariable.read();
            Array colArray = colVariable.read();
            int col;
            int row;


            for (int i = 0; i < nCamLength; i++) {
                for (int j = 0; j < nLineOlcLength; j++) {
                    for (int k = 0; k < nDetCamLength; k++) {
                        int[] position = {i, j, k};
                        // Type of variable of (row,col) might change with change of MISR format. Be careful here.
                        row = ((ArrayInt.D3) rowArray).get(i, j, k);
                        col = ((ArrayShort.D3) colArray).get(i, j, k);
                        if (col >= 0 && row >= 0) {
                            if (row < slstrNumRows && col < slstrNumCols) {
                                int[] colRowArray = {col, row};
                                colRowMap.put(colRowArray, position);
                            }
                        }
                    }
                }
            }
        }
        else {
            for (int longDimSplitter = 10000; longDimSplitter < nLineOlcLength+10000; longDimSplitter+=10000) {
                int step = 10000;
                if (longDimSplitter > nLineOlcLength) {
                    step = nLineOlcLength + step - longDimSplitter;
                    longDimSplitter = nLineOlcLength;
                }

                Array rowArray = rowVariable.read(new int[]{0, longDimSplitter-step, 0}, new int[]{nCamLength, step, nDetCamLength});
                Array colArray = colVariable.read(new int[]{0, longDimSplitter-step, 0}, new int[]{nCamLength, step, nDetCamLength});
                int col;
                int row;
                for (int i = 0; i < nCamLength; i++) {
                    for (int j = 0; j < step; j++) {
                        for (int k = 0; k < nDetCamLength; k++) {
                            int[] position = {i, j, k};
                            // Type of variable of (row,col) might change with change of MISR format. Be careful here.
                            row = ((ArrayInt.D3) rowArray).get(i, j, k);
                            col = ((ArrayShort.D3) colArray).get(i, j, k);
                            if (col >= 0 && row >= 0) {
                                if (row < slstrNumRows && col < slstrNumCols) {
                                    int[] colRowArray = {col, row};
                                    colRowMap.put(colRowArray, position);
                                }
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
        String bandName = "/misregist_Oref_S5.nc";

        String path = this.misrPath;
        String misrBandFile = path + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        int nLineOlcLength = netcdfFile.findDimension("N_LINE_OLC").getLength();
        int nDetCamLength = netcdfFile.findDimension("N_DET_CAM").getLength();
        int nCamLength = netcdfFile.findDimension("N_CAM").getLength();
        String rowVariableName = getRowVariableName(netcdfFile);
        String orphanVariableName = getOrphanVariableName(netcdfFile);
        Variable rowVariable = netcdfFile.findVariable(rowVariableName);
        Variable orphanVariable = netcdfFile.findVariable(orphanVariableName);
        Array rowArray = rowVariable.read();
        Array orphanArray = orphanVariable.read();

        int orphan = -1;
        int row = -1;
        TreeMap<int[], int[]> orphanRowMap = new TreeMap<>(new ComparatorIntArray());


        for (int i = 0; i < nCamLength; i++) {
            for (int j = 0; j < nLineOlcLength; j++) {
                for (int k = 0; k < nDetCamLength; k++) {
                    int[] position = {i, j, k};
                    if (orphanVariableName.matches("L1b_orphan_.._"+"an")) {
                        row = ((ArrayInt.D3) rowArray).get(i,j,k) ;
                        orphan = ((ArrayShort.D3) orphanArray).get(i,j,k) ;
                    } else if (orphanVariableName.matches("orphan_corresp_s._"+"an")) {
                        row = ((ArrayInt.D3) rowArray).get(i,j,k);
                        orphan = ((ArrayInt.D3) orphanArray).get(i,j,k);
                    }
                    if (orphan>0 && row>0) {
                        int[] orphanRowArray = {orphan, row};
                        orphanRowMap.put(orphanRowArray, position);
                    }
                }
            }
        }

        return orphanRowMap;
    }




    // Step 4.2
    private TreeMap getOlciMisrMap() throws IOException, InvalidRangeException {
        //should provide mapping between OLCI image grid and instrument grid

        TreeMap<int[], int[]> olciMap = new TreeMap<>(new ComparatorIntArray());
        String bandName = "/misreg_Oref_Oa17.nc";
        String path = this.misrPath;
        String misrBandFile = path + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        String rowVariableName = "L1b_row_17";
        String colVariableName = "L1b_col_17";
        Variable rowVariable = netcdfFile.findVariable(rowVariableName);
        Variable colVariable = netcdfFile.findVariable(colVariableName);
        //
        if (rowVariable==null) {
            rowVariableName = "delta_row_17";
            rowVariable = netcdfFile.findVariable(rowVariableName);

        }
        if (colVariable==null){
            colVariableName = "delta_col_17";
            colVariable = netcdfFile.findVariable(colVariableName);
        }
        //

        int nCamLength = netcdfFile.findDimension("N_CAM").getLength();
        int nLineOlcLength = netcdfFile.findDimension("N_LINE_OLC").getLength();
        int nDetCamLength = netcdfFile.findDimension("N_DET_CAM").getLength();

        Variable rowOffsetVariable = netcdfFile.findVariable("input_products_row_offset");
        int rowOffset = (int) rowOffsetVariable.readScalarInt();

        if (nLineOlcLength < 10000) {
            ArrayShort.D3 rowArray = (ArrayShort.D3) rowVariable.read();
            ArrayShort.D3 colArray = (ArrayShort.D3) colVariable.read();
            for (int i = 0; i < nCamLength; i++) {
                for (int j = 0; j < nLineOlcLength; j++) {
                    for (int k = 0; k < nDetCamLength; k++) {
                        short row = rowArray.get(i, j, k);
                        short col = colArray.get(i, j, k);
                        int rowNorm = row - rowOffset;
                        if (rowNorm >= 0 && col >= 0) {
                            if (rowNorm < olciNumRows && col < olciNumCols) {
                                int[] gridCoors = {i, j, k};
                                int[] imageCoors = {col, rowNorm};
                                olciMap.put(gridCoors, imageCoors);
                            }
                        }
                    }
                }
            }
        }

        else {

            for (int longDimSplitter = 10000; longDimSplitter < nLineOlcLength+10000; longDimSplitter+=10000) {
                int step = 10000;
                if (longDimSplitter > nLineOlcLength) {
                    step = nLineOlcLength + step -longDimSplitter;
                    longDimSplitter = nLineOlcLength;
                }
                ArrayShort.D3 rowArray = (ArrayShort.D3) rowVariable.read(new int[]{0, longDimSplitter-step, 0}, new int[]{nCamLength, step, nDetCamLength});
                ArrayShort.D3 colArray = (ArrayShort.D3) colVariable.read(new int[]{0, longDimSplitter-step, 0}, new int[]{nCamLength, step, nDetCamLength});
                for (int i = 0; i < nCamLength; i++) {
                    for (int j = 0; j < step ; j++) {
                        for (int k = 0; k < nDetCamLength; k++) {
                            short row = rowArray.get(i, j, k);
                            short col = colArray.get(i, j, k);
                            int rowNorm = row - rowOffset;
                            if ((rowNorm) >= 0 && col >= 0) {
                                if ( (rowNorm)  < olciNumRows && col < olciNumCols) {
                                    int[] gridCoors = {i, j, k};
                                    int[] imageCoors = {col, rowNorm};
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





    TreeMap getSlstrOlciMap() throws InvalidRangeException, IOException {
        //Provides mapping between SLSTR image grid and OLCI image grid
        TreeMap<int[], int[]> gridMapPixel = new TreeMap<>(new ComparatorIntArray() );

        // Old version of MISR transformation
        if (newTransform == false) {
            TreeMap misrOlciMap = getMisrOlciMap(); //3
            TreeMap olciImageMap = getOlciMisrMap(); // 4.2
            for (Iterator<Map.Entry<int[], int[]>> entries = misrOlciMap.entrySet().iterator(); entries.hasNext(); ) {
                Map.Entry<int[], int[]> entry = entries.next();
                int[] mjk = entry.getValue();
                int[] xy = (int[]) olciImageMap.get(mjk);
                if (xy != null) {
                    gridMapPixel.put(xy, entry.getKey());
                }
            }
        }
        // New version
        else if (newTransform == true) {
            //TreeMap<int[], int[]> gridMapPixel = new TreeMap<>(new ComparatorIntArray() );
            TreeMap<int[], int[]> gridMapOrphan = new TreeMap<>(new ComparatorIntArray() );
            TreeMap slstrImageMap = getSlstrImageMap(slstrImageProduct.getSceneRasterWidth(), slstrImageProduct.getSceneRasterHeight()); //1
            TreeMap slstrOrphanMap = getSlstrOrphanImageMap();
            TreeMap slstrMisrMap = getSlstrGridMisrMap(slstrImageMap,true); //2
            TreeMap slstrOrphanMisrMap = getSlstrGridMisrMap(slstrOrphanMap,true);
            TreeMap misrOlciMap = getMisrOlciMap(); //3
            TreeMap misrOrphanOlciMap = getMisrOlciOrphanMap();
            TreeMap olciImageMap = getOlciMisrMap(); // 4
            TreeMap olciImageOrphanMap = getOlciMisrMap(); // 4
            for (Iterator<Map.Entry<int[], int[]>> entries = slstrImageMap.entrySet().iterator(); entries.hasNext(); ) {
                Map.Entry<int[], int[]> entry = entries.next();
                int[] slstrScanPixDet = entry.getValue();
                int[] rowCol = (int[]) slstrMisrMap.get(slstrScanPixDet);
                int[] mjk = (int[]) misrOlciMap.get(rowCol);
                if (mjk != null) {
                    int[] xy = (int[]) olciImageMap.get(mjk);
                    if (xy!= null) {
                        gridMapPixel.put(xy, entry.getKey());
                    }
                }
            }
            for (Iterator<Map.Entry<int[], int[]>> entries = slstrOrphanMap.entrySet().iterator(); entries.hasNext(); ) {
                Map.Entry<int[], int[]> entry = entries.next();
                int[] slstrScanPixDet = entry.getValue();
                int[] rowOrphan = (int[]) slstrOrphanMisrMap.get(slstrScanPixDet);
                int[] mjk = (int[]) misrOrphanOlciMap.get(rowOrphan);
                if (mjk != null) {
                    int[] xy = (int[]) olciImageOrphanMap.get(mjk);
                    if (xy!= null) {
                        gridMapOrphan.put(xy, entry.getKey());
                    }
                }
            }

            TreeMap<int[], int[]> gridMap = new TreeMap<>(new ComparatorIntArray() );
            gridMap.putAll(gridMapOrphan);
            gridMap.putAll(gridMapPixel);
            return gridMap;


        }
        return gridMapPixel;
    }

    private String getRowVariableName(NetcdfFile netcdfFile) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getName().matches("L1b_row_.._"+"..") || variable.getName().matches("row_corresp_s._"+"..") || variable.getName().matches("L1b_row_"+"..")) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Row variable not found");
    }

    private String getColVariableName(NetcdfFile netcdfFile) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getName().matches("L1b_col_.._"+"..") || variable.getName().matches("col_corresp_s._"+"..") || variable.getName().matches("L1b_col_"+"..")) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Col variable not found");
    }

    private String getOrphanVariableName(NetcdfFile netcdfFile) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getName().matches("L1b_orphan_.._"+"an") || variable.getName().matches("orphan_corresp_s._"+"an") || variable.getName().matches("L1b_orphan_"+"an")) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Orphan variable not found");
    }

    //so far this value is not used, but may be needed in the future
    private int getSlstrS3Offset(Product slstrImageProduct) throws IOException{
        String path = slstrImageProduct.getFileLocation().getParent();
        String filePath = path + "/S3_radiance_an.nc";
        NetcdfFile netcdfFile = NetcdfFile.open(filePath);
        Number offsetAttribute = netcdfFile.findGlobalAttribute("start_offset").getNumericValue();
        System.out.println(offsetAttribute);
        System.out.println(filePath);
        int offsetValue = (int) offsetAttribute;
        netcdfFile.close();
        return  offsetValue;
    }

    public static class ComparatorIntArray implements java.util.Comparator<int[]>, Serializable{
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
