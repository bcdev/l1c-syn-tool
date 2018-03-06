package org.esa.s3tbx;

import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;

@OperatorMetadata(alias = "L1C-SYN",
        label = "L1C-SYN Tool",
        authors = "Marco Peters",
        copyright = "Brockmann Consult GmbH",
        version = "0.1")
public class L1cSynOp extends Operator {
    @Override
    public void initialize() throws OperatorException {

    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(L1cSynOp.class);
        }
    }

}
