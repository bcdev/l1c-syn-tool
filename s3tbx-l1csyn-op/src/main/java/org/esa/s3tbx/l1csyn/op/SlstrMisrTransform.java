package org.esa.s3tbx.l1csyn.op;

import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import ucar.ma2.Array;
import ucar.ma2.Index;
import ucar.ma2.InvalidRangeException;
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



    SlstrMisrTransform (Product OlciImageProduct, Product slstrImageProduct, File misrManifest){
        this.olciImageProduct = olciImageProduct;
        this.slstrImageProduct = slstrImageProduct;
        this.misrPath = misrManifest.getParent();
    }


    private int[] getSlstrGridScanDetectorPixel(int row, int col, NetcdfFile netcdfFile) throws IOException, InvalidRangeException {
        Variable scanVariable = netcdfFile.findVariable("scan_ao");
        Variable pixelVariable = netcdfFile.findVariable("pixel_ao");
        Variable detectorVariable = netcdfFile.findVariable("detector_ao");

        Array scanArray = scanVariable.read(new int[]{row,col}, new int[]{1,1});
        short scanValue = scanArray.getShort(0);

        Array pixelArray = pixelVariable.read(new int[]{row,col}, new int[]{1,1});
        short pixelValue = pixelArray.getShort(0);

        Array detectorArray = detectorVariable.read(new int[]{row,col}, new int[]{1,1});
        byte detectorValue = detectorArray.getByte(0);

        int[] finalArray = new int[3];
        finalArray[0] = scanValue;
        finalArray[1] = pixelValue;
        finalArray[2] = detectorValue;
        return finalArray;
    }

    private int[] getOlciGridPixelPosition (int f, int jL1b, NetcdfFile netcdfFile) throws IOException, InvalidRangeException {

        Variable detectorVariable = netcdfFile.findVariable("detector_index");
        Array detectorArray = detectorVariable.read(new int[]{f,jL1b}, new int[]{1,1});
        short detectorValue = detectorArray.getShort(0);

        int nDetCam = 1; //todo: check where this paramater comes from?
        short[] df = getOlciFrameOffset(netcdfFile);
        Short dfMin = Collections.min(Arrays.asList(ArrayUtils.toObject(df)));

        int p = detectorValue;
        int m = (int) Math.floor(p/nDetCam)+1;
        int j = p - (m-1)*nDetCam;
        int k = f -df[p]+dfMin;


        int[] finalArray = new int[3];
        finalArray[0]=m;
        finalArray[1]=k;
        finalArray[2]=j;
        return finalArray;
    }

    private int[] getColRow (int scan, int pixel, int detector){
        int row = 4*scan + detector;
        int col = pixel;
        int [] rowCol = new int[2];
        rowCol[0] = row;
        rowCol[1] = col;
        return rowCol;
    }

    private HashMap getMisrMap(String bandName) throws IOException, InvalidRangeException {
        String path = this.misrPath;
        String misrBandFile = path+bandName;
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
        double rowScaleFactor = rowVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        double rowOffset = rowVariable.findAttribute("add_offset").getNumericValue().doubleValue();
        double colScaleFactor = colVariable.findAttribute("scale_factor").getNumericValue().doubleValue();
        double colOffset = colVariable.findAttribute("add_offset").getNumericValue().doubleValue();


        HashMap<int[], int[]> rowColMap = new HashMap<>();
        for (int i=0; i<nLineOlcLength; i++){
            for (int j=0; j<nDetCamLength; j++){
                for (int k=0; k<nCamLength; k++){
                    int[] position = {i,j,k};
                    Index index = Index.factory(position);
                    int row = rowArray.getInt(index);//*rowScaleFactor + rowOffset;  //todo: clarify how to do rounding if we get float at this point.
                    int col = colArray.getInt(index);//*colScaleFactor + colOffset;
                    int[] rowColArray = {row,col};
                    rowColMap.put(position,rowColArray);
                }
            }
        }

        return rowColMap;
    }

    private HashMap getOlciGridImageMap(int x, int y) throws IOException, InvalidRangeException {
        //x and y are dimensions of OLCI L1B raster
        HashMap<int[], int[]> olciMap = new HashMap<>();

        String path = olciImageProduct.getFileLocation().getParent();
        String instrumentDataPath = path+"instrument_data.nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(instrumentDataPath);

        for (int i=0; i<x; i++){
            for (int j=0; j<y; j++){
                int[] gridPosition = getOlciGridPixelPosition(i,j ,netcdfFile);
                int[] imagePosition = new int[2];
                imagePosition[0]=i;
                imagePosition[1]=j;
                olciMap.put(imagePosition,gridPosition);
            }
        }
        return olciMap;
    }

    private HashMap getSlstrImageMap(int x, int y) throws IOException, InvalidRangeException{
        //x and y are dimensions of SLSTR L1B raster
        HashMap<int[], int[]> slstrMap = new HashMap<>();
        String path = slstrImageProduct.getFileLocation().getParent();
        String indexFilePath = path+"indices_ao.nc";
        NetcdfFile netcdfFile = NetcdfFileOpener.open(indexFilePath);
        for (int i=0; i<x; i++){
            for (int j=0; j<y; j++) {
                int[] imagePosition = new int[2];
                imagePosition[0]=i;
                imagePosition[1]=j;
                int [] gridPosition = getSlstrGridScanDetectorPixel(x,y,netcdfFile);
                slstrMap.put(imagePosition, gridPosition);
            }
        }
        return slstrMap;
    }

    private HashMap getOlciGridSlstrGridMap(Map mapSlstr ) {
        HashMap<int[], int[]> gridMap = new HashMap<>();

        for (Object value : mapSlstr.values()){
            int[] scanPixelnDetector = (int[]) value;
            int scan = scanPixelnDetector[0];
            int pixel = scanPixelnDetector[1];
            int detector = scanPixelnDetector[2];
            int[] rowCol = getColRow(scan,pixel,detector);
            gridMap.put(scanPixelnDetector,rowCol);
        }

        return gridMap;
    }


    private short[] getOlciFrameOffset(NetcdfFile netcdfFile) throws IOException{
        Variable frameVariable =  netcdfFile.findVariable("frame_offset");
        Dimension detectorDimenstion = netcdfFile.findDimension("detectors");
        int dimensionLength = detectorDimenstion.getLength();
        Array frameArray = frameVariable.read();
        short[] frameShort = (short[]) frameArray.copyTo1DJavaArray();
        if (frameShort.length == dimensionLength) {
            return frameShort;
        }
        else {
            throw new OperatorException("error while reading OLCI frame offset");
        }
    }


    //All transforms
    private int[] getSlstrPosOnOlciGrid() throws InvalidRangeException,IOException{
        int[] positionOnOlciGrid= new int[2];

        // map (x,y) -> (scan,pixel,detector)
        HashMap slstrImageMap = getSlstrImageMap(slstrImageProduct.getSceneRasterWidth(),slstrImageProduct.getSceneRasterHeight());


        return positionOnOlciGrid;
    }



    private HashMap getSlstrOlciMap(int scan, int detector, int pixel ){
        HashMap<int[], int[]> olciGridSlstrGrid = new HashMap<>();
        return olciGridSlstrGrid;
    }


    private String getRowVariableName(NetcdfFile netcdfFile){
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables){
            if (variable.getName().toLowerCase().contains("row")){
                return variable.getName();
            }
        }
        throw new NullPointerException("Row variable not found");
    }

    private String getColVariableName(NetcdfFile netcdfFile){
        List<Variable> variables = netcdfFile.getVariables();
        for (Variable variable : variables){
            if (variable.getName().toLowerCase().contains("col")){
                return variable.getName();
            }
        }
        throw new NullPointerException("Col variable not found");

    }

    private void readSlstrIndexFile(){
    }

    private void readOlciIndexFile(){
    }

    private void readSlstrOrphanPixels(){
    }

    private void readOlciRemovedPixels(){
    }


}
