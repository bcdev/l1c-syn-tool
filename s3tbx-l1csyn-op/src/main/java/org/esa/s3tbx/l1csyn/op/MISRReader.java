package org.esa.s3tbx.l1csyn.op;

import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import org.esa.snap.rcp.SnapApp;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class MISRReader {

    final File misrFile;

    private final static String[] OLCIBANDS = {
            "Oa01_radiance", "Oa02_radiance", "Oa03_radiance", "Oa04_radiance", "Oa05_radiance",
            "Oa06_radiance", "Oa07_radiance", "Oa08_radiance", "Oa09_radiance", "Oa10_radiance",
            "Oa11_radiance", "Oa12_radiance", "Oa13_radiance", "Oa14_radiance", "Oa15_radiance",
            "Oa16_radiance", "Oa17_radiance", "Oa18_radiance", "Oa19_radiance", "Oa20_radiance",
            "Oa21_radiance"
    };

    private final static String[] SLSTRnadirBANDS = {"S1","S2","S3","S4","S5","S6","S7","S8","S9","F1","F2"};

    private final static String[] SLSTRobliqueBANDS = {"ao","bo","co","io"};

    MISRReader(File misrFile){
        this.misrFile = misrFile;
    }

    void readMisrProduct()  throws IOException,InvalidRangeException  {
        String misrFolder = misrFile.getParent();
        Map fileBandMap = getFileBandMap();

        for (Object fileName : fileBandMap.values()){
            String fullNCPath = misrFolder+"/"+fileName;
            readMisrNetCDF(fullNCPath);
        }
    }


    private void readMisrNetCDF(String ncFilename) throws IOException,InvalidRangeException {
        try {
            File file = new File(ncFilename);
            if (file.exists()) {
                NetcdfFile netcdfFile = NetcdfFileOpener.open(file);
                //readVariable(getRowVariableName(ncFilename),netcdfFile);
                readVariable(getRowVariableName(netcdfFile),netcdfFile);
                //readVariable(getColVariableName(ncFilename),netcdfFile);
                readVariable(getColVariableName(netcdfFile),netcdfFile);
                SnapApp.getDefault().getLogger().info("file "+file.getName()+" was read successfully...");
            }
            else {
                SnapApp.getDefault().getLogger().info(file.getName()+" is not present in the MISR file");
            }
        }
        catch(IOException e){
        }
    }


    private void readVariable(String variableName, NetcdfFile netcdfFile) throws IOException,InvalidRangeException{
        Variable variable = netcdfFile.findVariable(variableName);
        double scaleFactor = variable.findAttribute("scale_factor").getNumericValue().doubleValue();
        double offset = variable.findAttribute("add_offset").getNumericValue().doubleValue();
        //double testOnly = extractDouble(variable,0,0,0,scaleFactor,offset);
        //System.out.println(testOnly+" "+variableName+" "+netcdfFile.getCacheName());
    }

    private double extractDouble(Variable variable, int line,int  detector,int camera , double scaleFactor, double offset ) throws InvalidRangeException,IOException {
        Array array = variable.read(new int[]{camera, line, detector}, new int[]{1,1,1});
        if ( array.getInt(0) == variable.findAttribute("_FillValue").getNumericValue().intValue() ){
            return Double.NaN;
        }
        double value = array.getInt(0)*scaleFactor+offset;
        return value;
    }

    private Array extractDoubleArray(Variable variable, int lineStart, int detectorStart,
                                     int cameraStart, int lineShape, int detectorShape,
                                     int cameraShape, double scaleFactor) throws InvalidRangeException,IOException{

        Array array = variable.read(new int[]{cameraStart,lineStart ,detectorStart}, new int[]{cameraShape,lineShape,detectorShape});

        return array;
    }



    private static Map<String, String> getFileBandMap(){
        Map<String, String> map = new HashMap<>();
        for (String olciBandName : OLCIBANDS){
            map.put(olciBandName,"misreg_Oref_"+olciBandName.substring(0,4)+".nc");
        }
        for (String slstrNadirBandName : SLSTRnadirBANDS){
            map.put(slstrNadirBandName,"misregist_Oref_"+slstrNadirBandName+".nc");
        }
        for (String slstrObliqBandName: SLSTRobliqueBANDS){
            map.put(slstrObliqBandName,"misregist_Oref_"+slstrObliqBandName+".nc");
        }
        return map;
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

    private String getRowVariableName(String ncFileName) throws  IOException{
        if (ncFileName.matches("misreg_Oref_Oa...nc"))  {
            return "delta_row_"+ncFileName.substring(14,16);
        }
        else if (ncFileName.matches("misregist_Oref_[SF]..nc")){
            return "row_corresp_"+ncFileName.substring(15,17).toLowerCase()+"_an";
        }
        else if (ncFileName.matches("misregist_Oref_[abci]o.nc")) {
            return "row_corresp_"+ncFileName.substring(15,17);
        }
        throw new IOException("Error while trying to read "+ncFileName+" file");

    }

    private String getColVariableName(String ncFileName) throws  IOException{
        if (ncFileName.matches("misreg_Oref_Oa...nc"))  {
            return "delta_col_"+ncFileName.substring(14,16);
        }
        else if (ncFileName.matches("misregist_Oref_[SF]..nc")){
            return "col_corresp_"+ncFileName.substring(15,17).toLowerCase()+"_an";
        }
        else if (ncFileName.matches("misregist_Oref_[abci]o.nc")) {
            return "col_corresp_"+ncFileName.substring(15,17);
        }
        throw new IOException("Error while trying to read "+ncFileName+" file");
    }

}
