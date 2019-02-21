package org.esa.s3tbx.l1csyn.op.ui;

import org.esa.snap.core.dataio.ProductSubsetDef;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.rcp.SnapApp;
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
     private Product product;
     private ProductSubsetDialog dialog;
     private L1cSynDialog parentDialog;
     private String type;

     public L1cSynSubsetAction(L1cSynDialog parentDialog,String type){
         this.parentDialog = parentDialog;
         this.type = type;
     }

    @Override
    public void actionPerformed(ActionEvent e)  {
        final AppContext appContext = getAppContext();

        Window window = appContext.getApplicationWindow();
        if (type.equals("OLCI")){
            product=parentDialog.createSourceProductsMap().get("olciProduct");
        }
        else if (type.equals("SLSTR")){
            product=parentDialog.createSourceProductsMap().get("slstrProduct");

        }
        if (product == null) {
            throw new OperatorException("Please choose "+type+" product for subsetting");
        }
        dialog = new ProductSubsetDialog(window,product);
        JButton runButton = (JButton) dialog.getButton(1);
        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ProductSubsetDef subsetDef =  dialog.getProductSubsetDef();

                try {
                    Product subProduct = product.createSubset(subsetDef, "subset_"+product.getName(), product.getDescription());
                    parentDialog.setSourceProduct(type,subProduct);
                }
                catch (Exception exception) {
                    final String msg = "An error occurred while creating the product subset:\n" +
                            exception.getMessage();
                    SnapApp.getDefault().handleError(msg, exception);
                }
                System.out.println("Remove this in production");
            }
        });
        dialog.getJDialog().pack();
        dialog.show();
    }



}
