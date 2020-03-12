package org.esa.l1csyn.coreg.lib;


import java.io.File;

public class L1cSynCoreg {

    final private static String SLSTR_KEYWORD = "-slstrProduct";
    final private static String OLCI_KEYWORD = "-olciProduct";
    final private static String MISR_KEYWORD = "-misrProduct";
    static File olciProductPath = null;
    static File slstrProductPath = null;
    static File misrProductPath = null;

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
        System.out.println(1);
        System.out.println(2);

        System.out.println(3);
        if (args.length>0) {
            olciProductPath = parseFromKeyword(OLCI_KEYWORD, args);
            slstrProductPath = parseFromKeyword(SLSTR_KEYWORD, args);
            misrProductPath = parseFromKeyword(MISR_KEYWORD, args);
        }
        if (slstrProductPath==null || olciProductPath==null ) {
            throw  new Exception("product path is not set correctly");
        } else {
            SlstrMisrTransformation transformation = new SlstrMisrTransformation(slstrProductPath,olciProductPath,misrProductPath,null);
        }
        System.out.println(3);



        String currentLoc = System.getProperty("user.dir");
        System.out.println(4);

    }


    private static File parseFromKeyword( String keyword, String... args){
        String stringPath = null;
        for (int i=0 ; i<args.length-1; i++) {
            if (args[i].equals(keyword)){
                stringPath = args[i+1] ;
            }
        }

        return new File(stringPath);
    }
}
