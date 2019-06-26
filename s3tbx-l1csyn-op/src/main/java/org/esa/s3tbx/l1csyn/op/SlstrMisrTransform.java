package org.esa.s3tbx.l1csyn.op;

import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import ucar.ma2.*;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SlstrMisrTransform {
    private Product olciImageProduct;
    private Product slstrImageProduct;
    private String misrPath;

    private int N_DET_CAM = 740;

    SlstrMisrTransform(Product olciImageProduct, Product slstrImageProduct, File misrManifest) {
        this.olciImageProduct = olciImageProduct;
        this.slstrImageProduct = slstrImageProduct;
        this.misrPath = misrManifest.getParent();
    }

    private int[] getColRow(int scan, int pixel, int detector) {
        //todo : clarify the formula
        int row = scan * 4 + detector;
        int col = pixel;
        int[] rowCol = new int[2];
        rowCol[0] = row;
        rowCol[1] = col;
        return rowCol;
    }

    private TreeMap<int[], int[]> getMisrOlciMap() throws IOException, InvalidRangeException {
        // provides mapping between MISR (row/col) and OLCI instrument grid (N_LINE_OLC/N_DET_CAM/N_CAM) from MISR product
        String bandName = "/misregist_Oref_S5.nc";
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
        Array rowArray = rowVariable.read();
        Array colArray = colVariable.read();
        // double rowScaleFactor = rowVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        // double rowOffset = rowVariable.findAttribute("add_offset").getNumericValue().doubleValue();
        // double colScaleFactor = colVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        // double colOffset = colVariable.findAttribute("add_offset").getNumericValue().doubleValue();
        int col = -1;
        int row = -1;
        TreeMap<int[], int[]> rowColMap = new TreeMap<>(intArrayComparator());
        int minRow;
        int minCol;
        minRow = (int) MAMath.getMinimum(rowArray);
        minCol = (int) MAMath.getMinimum(colArray);

        for (int i = 0; i < nCamLength; i++) {
            for (int j = 0; j < nLineOlcLength; j++) {
                for (int k = 0; k < nDetCamLength; k++) {
                    int[] position = {k, i, j};
                    //todo: clarify how to do rounding if we get float at this point.
                    //int row  = (int) Math.floor(((ArrayInt.D3) rowArray).get(k,i,j)*rowScaleFactor + rowOffset);
                    //int col  = (int) Math.floor(((ArrayInt.D3) colArray).get(k,i,j)*colScaleFactor + colOffset);
                    if (colVariableName.matches("L1b_col_.._an")) {
                        //row = ((ArrayInt.D3) rowArray).get(i, j, k) - minRow;
                        row = ((ArrayInt.D3) rowArray).get(i, j, k) ;
                        //col = ((ArrayShort.D3) colArray).get(i, j, k) - minCol;
                        col = ((ArrayShort.D3) colArray).get(i, j, k) ;
                    } else if (colVariableName.matches("col_corresp_s._an")) {
                        //row  = (int) Math.floor( ( ((ArrayInt.D3) rowArray).get(i,j,k)  )*rowScaleFactor + rowOffset);
                        //col  = (int) Math.floor( ( ((ArrayInt.D3) colArray).get(i,j,k)  )*colScaleFactor + colOffset);
                        //row = (int) Math.floor(((ArrayInt.D3) rowArray).get(i, j, k) - minRow);
                        row = (int) Math.floor(((ArrayInt.D3) rowArray).get(i, j, k) );
                        //col = (int) Math.floor(((ArrayInt.D3) colArray).get(i, j, k) - minCol);
                        col = (int) Math.floor(((ArrayInt.D3) colArray).get(i, j, k) );
                    }

                    //int[] rowColArray = {row, col};
                     int[] rowColArray = {col, row};


                    rowColMap.put(rowColArray, position);
                }
            }
        }

        return rowColMap;
    }

    private TreeMap getOlciMisrMap() throws IOException {
        TreeMap<int[], int[]> olciMap = new TreeMap<>(intArrayComparator());
        String bandName = "/misreg_Oref_Oa17";
        String path = this.misrPath;
        String misrBandFile = path + bandName;
        NetcdfFile netcdfFile = NetcdfFile.open(misrBandFile);
        String rowVariableName = "L1b_row_17";
        String colVariableName = "L1b_col_17";

        return olciMap;
    }

    private TreeMap getOlciGridImageMap(int x, int y) throws IOException, InvalidRangeException {
        // Provides mapping between OLCI image grid(x,y) and OLCI instrument grid(m,j,k)
        //x and y are dimensions of OLCI L1B raster
        TreeMap<int[], int[]> olciMap = new TreeMap<>(intArrayComparator());

        String path = olciImageProduct.getFileLocation().getParent();
        String instrumentDataPath = path + "/instrument_data.nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(instrumentDataPath);

        Variable detectorVariable = netcdfFile.findVariable("detector_index");
        Array detectorArray = detectorVariable.read();
        int nDetCam = N_DET_CAM; //todo: check where this parameter comes from?
        short[] df = getOlciFrameOffset(netcdfFile);
        Short dfMin = Collections.min(Arrays.asList(ArrayUtils.toObject(df)));
        int[] shape = detectorArray.getShape();
        for (int f = 0; f < x; f++) {
            for (int j1L1b = 0; j1L1b < y; j1L1b++) {
                int[] position = {j1L1b, f};
                short detectorValue = ((ArrayShort.D2) detectorArray).get(j1L1b, f);
                if (detectorValue != -1) {
                    int p = detectorValue;
                    int m = (int) Math.floor(p / nDetCam) + 1;
                    int j = p - (m - 1) * nDetCam;
                    int k = j1L1b - df[p] + dfMin;
                    int[] finalArray = new int[3];
                    finalArray[0] = k;
                    finalArray[1] = m;
                    finalArray[2] = j;
                    olciMap.put(finalArray, position);
                }
            }
        }
        return olciMap;
    }

    private TreeMap getSlstrImageMap(int x, int y) throws IOException, InvalidRangeException {
        // Provides mapping between SLSTR image grid(x,y) and SLSTR instrument grid(scan,pixel,detector)
        //x and y are dimensions of SLSTR L1B raster
        TreeMap<int[], int[]> slstrMap = new TreeMap<>(intArrayComparator());
        String path = slstrImageProduct.getFileLocation().getParent();
        String indexFilePath = path + "/indices_an.nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(indexFilePath);
        Variable scanVariable = netcdfFile.findVariable("scan_an");
        Variable pixelVariable = netcdfFile.findVariable("pixel_an");
        Variable detectorVariable = netcdfFile.findVariable("detector_an");

        Array scanArray = scanVariable.read();
        Array pixelArray = pixelVariable.read();
        Array detectorArray = detectorVariable.read();

        Variable scanOffsetVariable = netcdfFile.findVariable("l0_scan_offset_an");
        int scanOffset = scanOffsetVariable.readScalarInt();
        //todo remove later
        scanOffset = 0;

        for (int i = 0; i < x; i++) {
            for (int j = 0; j < y; j++) {
                int[] imagePosition = {j, i};
                short scan = (short) (((ArrayShort.D2) scanArray).get(j, i) + scanOffset);
                short pixel = ((ArrayShort.D2) pixelArray).get(j, i);
                byte detector = ((ArrayByte.D2) detectorArray).get(j, i);
                int[] gridPosition = {scan, pixel, detector};
                if (scan != -1 && pixel != -1 && detector != -1) {
                    slstrMap.put(imagePosition, gridPosition);
                }
            }
        }
        return slstrMap;
    }

    private TreeMap getSlstrGridMisrMap(Map mapSlstr) {
        //provides map between SLSTR instrument grid (scan,pixel,detector) and MISR file (row,col)
        TreeMap<int[], int[]> gridMap = new TreeMap<>(intArrayComparator());
        //test block to rescale row-col
        // todo: optimize
        int minRow = 99999999;
        int minCol = 99999999;
        for (Object value : mapSlstr.values()) {
            int[] scanPixelDetector = (int[]) value;
            int scan = scanPixelDetector[0];
            int pixel = scanPixelDetector[1];
            int detector = scanPixelDetector[2];
            int[] rowCol = getColRow(scan, pixel, detector);
            if (rowCol[0] < minRow) {
                minRow = rowCol[0];
            }
            if (rowCol[1] < minCol) {
                minCol = rowCol[1];
            }
        }

        for (Object value : mapSlstr.values()) {
            int[] scanPixelDetector = (int[]) value;
            int scan = scanPixelDetector[0];
            int pixel = scanPixelDetector[1];
            int detector = scanPixelDetector[2];
            int[] rowCol = getColRow(scan, pixel, detector);
            rowCol[0] = rowCol[0] - minRow;
            rowCol[1] = rowCol[1] - minCol;
            gridMap.put(scanPixelDetector, rowCol);
        }
        return gridMap;
    }

    private short[] getOlciFrameOffset(NetcdfFile netcdfFile) throws IOException {
        Variable frameVariable = netcdfFile.findVariable("frame_offset");
        Dimension detectorDimenstion = netcdfFile.findDimension("detectors");
        int dimensionLength = detectorDimenstion.getLength();
        Array frameArray = frameVariable.read();
        short[] frameShort = (short[]) frameArray.copyTo1DJavaArray();
        if (frameShort.length == dimensionLength) {
            return frameShort;
        } else {
            throw new OperatorException("error while reading OLCI frame offset");
        }
    }

    TreeMap getSlstrOlciMap() throws InvalidRangeException, IOException {
        //Provides mapping between SLSTR image grid and OLCI image grid
        //todo: find faster way to implement it.
        TreeMap<int[], int[]> gridMap = new TreeMap<>(intArrayComparator());

        TreeMap slstrImageMap = getSlstrImageMap(slstrImageProduct.getSceneRasterWidth(), slstrImageProduct.getSceneRasterHeight());
        TreeMap slsrtMisrMap = getSlstrGridMisrMap(slstrImageMap);
        TreeMap misrOlciMap = getMisrOlciMap();
        TreeMap olciImageMap = getOlciGridImageMap(olciImageProduct.getSceneRasterWidth(), olciImageProduct.getSceneRasterHeight());
        for (Iterator<Map.Entry<int[], int[]>> entries = slstrImageMap.entrySet().iterator(); entries.hasNext(); ) {
            Map.Entry<int[], int[]> entry = entries.next();
            int[] slstrScanPixDet = entry.getValue();
            int[] rowCol = (int[]) slsrtMisrMap.get(slstrScanPixDet);
            int[] mjk = (int[]) misrOlciMap.get(rowCol);
            if (mjk != null) {
                int[] xy = (int[]) olciImageMap.get(mjk);
                gridMap.put(entry.getKey(), xy);
            }
        }
        return gridMap;
    }

    private int[] getSlstrPosOnOlciGrid(int x, int y) throws InvalidRangeException, IOException {
        // Provides a relation between SLSTR and OLCI image grids for a single SLSTR pixel(x,y) on OL
        TreeMap<int[], int[]> gridMap = getSlstrOlciMap();
        int[] key = {x, y};
        int[] positionOnOlciGrid = gridMap.get(key);
        return positionOnOlciGrid;
    }

    private String getRowVariableName(NetcdfFile netcdfFile) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getName().matches("L1b_row_.._an") || variable.getName().matches("row_corresp_s._an")) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Row variable not found");
    }

    private String getColVariableName(NetcdfFile netcdfFile) {
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables) {
            if (variable.getName().matches("L1b_col_.._an") || variable.getName().matches("col_corresp_s._an")) {
                return variable.getName();
            }
        }
        throw new NullPointerException("Col variable not found");
    }

    public static Comparator<int[]> intArrayComparator() {
        return (left, right) -> {
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
        };
    }
}
