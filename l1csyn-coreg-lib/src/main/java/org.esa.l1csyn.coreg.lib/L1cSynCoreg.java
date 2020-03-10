package org.esa.l1csyn.coreg.lib;

import org.esa.s3tbx.l1csyn.op.L1cSynOp;
import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.main.CommandLineTool;
import org.esa.snap.core.gpf.main.GPT;
import org.esa.snap.core.util.SystemUtils;

import java.io.File;
import java.util.Locale;

public class L1cSynCoreg {

    public static void main(String... args){

        try {
            run(args);
        } catch (Throwable e) {
            String message;
            if (e.getMessage() != null) {
                message = e.getMessage();
            } else {
                message = e.getClass().getName();
            }
            System.err.println("\nError: " + message);
            System.exit(1);
        }
    }

    public static void run(String[] args) throws Exception {
        Product slstrProduct = null;
        Product olciProduct = null;

        L1cSynOp l1cSynOp = new L1cSynOp();
        l1cSynOp.setParameterDefaultValues();

        File olciProductPath = parseOlci(args);
        File slstrProductPath = parseSlsrt(args);
        if (slstrProductPath!=null ){
            slstrProduct = ProductIO.readProduct(slstrProductPath);
        }
        if (olciProductPath!=null){
            olciProduct = ProductIO.readProduct(olciProductPath);
        }



        l1cSynOp.setSourceProduct("slstrSource",slstrProduct);
        l1cSynOp.setSourceProduct("olciSource",olciProduct);
        Product synProduct = l1cSynOp.getTargetProduct();
    }



}
