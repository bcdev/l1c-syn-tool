package org.esa.s3tbx.l1csyn.op.ui;

import org.esa.snap.core.datamodel.Product;
import org.esa.snap.rcp.actions.AbstractSnapAction;
import org.esa.snap.ui.AppContext;
import org.esa.snap.ui.product.ProductSubsetDialog;
import org.openide.awt.ActionID;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


@ActionID(category = "Preprocessing", id = "org.esa.s3tbx.l1csyn.op.ui.L1cSynSubsetAction" )


public class L1cSynSubsetAction extends AbstractSnapAction implements ActionListener {
     private Product product;

    @Override
    public void actionPerformed(ActionEvent e) {
        final AppContext appContext = getAppContext();

        product = L1cSynDialog.getSourceProducts()[0];
        if (product ==null){
            throw new IllegalArgumentException("No product selected");
        }
        Window window = appContext.getApplicationWindow();

        /*String quickLookBand = ProductUtils.findSuitableQuicklookBandName(product);
        Band[] bands = product.getBands();
        for (Band band : bands) {
            if (!band.getName().equals(quickLookBand)) {
                product.removeBand(band);
            }
        }

        TiePointGrid[] tiePointGrids = product.getTiePointGrids();
        for (TiePointGrid tiePointGrid : tiePointGrids){
            product.removeTiePointGrid(tiePointGrid);
        }
        MetadataElement[] elements = product.getMetadataRoot().getElements();
        for (MetadataElement element : elements){
            product.getMetadataRoot().removeElement(element);
        }*/

        ProductSubsetDialog dialog = new ProductSubsetDialog(window,product);
        dialog.getJDialog().pack();
        dialog.show();



    }
}
