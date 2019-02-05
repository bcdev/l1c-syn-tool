package org.esa.s3tbx.l1csyn.op.ui;


import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.gpf.ui.TargetProductSelectorModel;

/**
 * Listener for selection of source product
 * @version $Revision: $ $Date:  $
 */
class SourceProductSelectionListener implements SelectionChangeListener {

    private TargetProductSelectorModel targetProductSelectorModel;
    private String targetProductNameSuffix;

    SourceProductSelectionListener(TargetProductSelectorModel targetProductSelectorModel,
                                   String targetProductNameSuffix) {
        this.targetProductSelectorModel = targetProductSelectorModel;
        this.targetProductNameSuffix = targetProductNameSuffix;
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        final Product selectedProduct = (Product) event.getSelection().getSelectedValue();

        if (selectedProduct != null) {
            // convert to L1CSyn specific product name
            final String l1cSynName = selectedProduct.getName();
            targetProductSelectorModel.setProductName(l1cSynName + targetProductNameSuffix);
        }
    }

    @Override
    public void selectionContextChanged(SelectionChangeEvent event) {
        // no actions
    }

}
