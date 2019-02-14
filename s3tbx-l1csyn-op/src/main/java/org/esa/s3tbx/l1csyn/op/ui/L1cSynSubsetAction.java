package org.esa.s3tbx.l1csyn.op.ui;

import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.TiePointGrid;
import org.esa.snap.rcp.actions.AbstractSnapAction;
import org.esa.snap.ui.AppContext;
import org.esa.snap.ui.product.ProductSubsetDialog;
import org.openide.awt.ActionID;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


@ActionID(category = "Preprocessing", id = "org.esa.s3tbx.l1csyn.op.ui.L1cSynSubsetAction" )


public class L1cSynSubsetAction extends AbstractSnapAction implements ActionListener {
     private Product olciProduct;
     private Product slstrProduct;
     private ProductSubsetDialog dialog;
     private L1cSynDialog parentDialog;

     public L1cSynSubsetAction(L1cSynDialog parentDialog){
         this.parentDialog = parentDialog;
     }

    @Override
    public void actionPerformed(ActionEvent e) {
        final AppContext appContext = getAppContext();

        olciProduct = L1cSynDialog.getSourceProducts()[0];
        slstrProduct = L1cSynDialog.getSourceProducts()[1];
        Product product = createProduct(olciProduct,slstrProduct);
        Window window = appContext.getApplicationWindow();

        dialog = new ProductSubsetDialog(window,product);

        JButton runButton = (JButton) dialog.getButton(1);
        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                parentDialog.setParameters(dialog.getProductSubsetDef());
                System.out.println("Do Something Clicked");
            }
        });
        dialog.getJDialog().pack();
        dialog.show();
        dialog.getJDialog().setVisible(false);

    }


    private Product createProduct(Product olciProduct, Product slstrProduct){
       if (olciProduct==null && slstrProduct==null) {
           throw new IllegalArgumentException("No product selected");
       }
       else if (slstrProduct==null) {
           return olciProduct;
       }
       else if (olciProduct==null) {
           return  slstrProduct;
       }
       else {
           Band[] slstrBands = slstrProduct.getBands();
           for (Band band : slstrBands){
               if (! olciProduct.containsBand(band.getName())) {
                   olciProduct.addBand(band);
               }
           }
           TiePointGrid[] slstrTiePointGrids = slstrProduct.getTiePointGrids();
           for (TiePointGrid tiePointGrid : slstrTiePointGrids){
               if (! olciProduct.containsTiePointGrid(tiePointGrid.getName())) {
                   olciProduct.addTiePointGrid(tiePointGrid);
               }
           }
           MetadataElement[] elements = slstrProduct.getMetadataRoot().getElements();
           for (MetadataElement element : elements){
               if (! olciProduct.getMetadataRoot().containsElement(element.getName())) {
                   olciProduct.getMetadataRoot().addElement(element);
               }
           }
           return  olciProduct;
       }
    }

}
