package org.esa.s3tbx.l1csyn.op.ui;

import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import com.bc.ceres.swing.selection.SelectionChangeEvent;
import com.bc.ceres.swing.selection.SelectionChangeListener;
import org.esa.s3tbx.l1csyn.op.L1cSynUtils;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductFilter;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.annotations.ParameterDescriptorFactory;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.ui.*;
import org.esa.snap.ui.AppContext;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.lang.reflect.Field;
import java.util.*;

public class L1cSynDialog extends SingleTargetProductDialog {

    private final String operatorName;
    private final org.esa.snap.ui.AppContext appContext;
    private final String helpID;

    private List<SourceProductSelector> sourceProductSelectorList;
    private Map<Field, SourceProductSelector> sourceProductSelectorMap;
    private Map<String, Object> parameterMap;
    private JTabbedPane form;
    private OperatorSpi operatorSpi;

    /*
     * DefaultDialog constructor
     */
    L1cSynDialog(String operatorName, AppContext appContext, String title, String helpID) {
        super(appContext, title, helpID);
        this.helpID = helpID;
        this.operatorName = operatorName;
        this.appContext = appContext;
        initialize(operatorName);
    }

    @Override
    protected Product createTargetProduct() {
        final HashMap<String, Product> sourceProducts = createSourceProductsMap();
        return GPF.createProduct(operatorName, parameterMap, sourceProducts);
    }

    @Override
    public int show() {
        initSourceProductSelectors();
        setContent(form);
        return super.show();
    }

    @Override
    public void hide() {
        releaseSourceProductSelectors();
        super.hide();
    }

    private void initComponents() {
        // Fetch source products
        setupSourceProductSelectorList(operatorSpi);
        if (!sourceProductSelectorList.isEmpty()) {
            setSourceProductSelectorToolTipTexts();
        }
        final TableLayout tableLayout = new TableLayout(1);
        tableLayout.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayout.setTableWeightX(1.0);
        tableLayout.setTableFill(TableLayout.Fill.HORIZONTAL);
        tableLayout.setTablePadding(3, 3);
        JPanel ioParametersPanel = new JPanel(tableLayout);
        
        final TableLayout tableLayoutSPG = new TableLayout(1);
        tableLayoutSPG.setTableAnchor(TableLayout.Anchor.WEST);
        tableLayoutSPG.setTableWeightX(1.0);
        tableLayoutSPG.setTableFill(TableLayout.Fill.HORIZONTAL);
        JPanel sourcesPanel = new JPanel(tableLayoutSPG);
        sourcesPanel.setBorder(BorderFactory.createTitledBorder("Source Products"));
        for (SourceProductSelector selector : sourceProductSelectorList) {
            sourcesPanel.add(selector.createDefaultPanel(""));
        }
        ioParametersPanel.add(sourcesPanel);
        
        TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();
        targetProductSelectorModel.setFormatName("NetCDF4-CF");
        //targetProductSelectorModel.setProductName("default_SYN_NAME");
        ioParametersPanel.add(getTargetProductSelector().createDefaultPanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());
        form.add("I/O Parameters", ioParametersPanel);
        OperatorParameterSupport parameterSupport = new OperatorParameterSupport(operatorSpi.getOperatorDescriptor(),
                null,
                parameterMap,
                null);
        OperatorMenu menuSupport = new OperatorMenu(this.getJDialog(),
                operatorSpi.getOperatorDescriptor(),
                parameterSupport,
                appContext,
                helpID);
        getJDialog().setJMenuBar(menuSupport.createDefaultMenu());
    }


    private void setupSourceProductSelectorList(OperatorSpi operatorSpi) {
        sourceProductSelectorList = new ArrayList<>(2);
        sourceProductSelectorMap = new HashMap<>(2);
        final Field[] fields = operatorSpi.getOperatorClass().getDeclaredFields();
        for (Field field : fields) {
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (annot != null) {
                final ProductFilter productFilter = new AnnotatedSourceProductFilter(annot);
                String label = String.format("%s:", annot.label().isEmpty() ? "Name" : annot.label());
                SourceProductSelector sourceProductSelector = new SourceProductSelector(appContext, label);
                sourceProductSelector.setProductFilter(productFilter);
                sourceProductSelectorList.add(sourceProductSelector);
                sourceProductSelectorMap.put(field, sourceProductSelector);
            }
        }
    }

    private HashMap<String, Product> createSourceProductsMap() {
        final HashMap<String, Product> sourceProducts = new HashMap<>(2);
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);
            String key = field.getName();
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            if (!annot.alias().isEmpty()) {
                key = annot.alias();
            }
            sourceProducts.put(key, selector.getSelectedProduct());
        }
        return sourceProducts;
    }

    private static class AnnotatedSourceProductFilter implements ProductFilter {
        private final SourceProduct annot;

        private AnnotatedSourceProductFilter(SourceProduct annot) {
            this.annot = annot;
        }

        @Override
        public boolean accept(Product product) {
            if (!annot.type().isEmpty() && !product.getProductType().matches(annot.type())) {
                return false;
            }
            for (String bandName : annot.bands()) {
                if (!product.containsBand(bandName)) {
                    return false;
                }
            }
            return true;
        }
    }

    private void initialize(String operatorName) {
        operatorSpi = GPF.getDefaultInstance().getOperatorSpiRegistry().getOperatorSpi(operatorName);
        if (operatorSpi == null) {
            throw new IllegalArgumentException("operatorName");
        }
        parameterMap = new HashMap<>(10);
        form = new JTabbedPane();
        initComponents();
        final PropertyContainer propertyContainer =
                PropertyContainer.createMapBacked(parameterMap, operatorSpi.getOperatorClass(),
                        new ParameterDescriptorFactory());
        addFormParameterPane(propertyContainer, form);
    }

    private void addFormParameterPane(PropertyContainer propertyContainer, JTabbedPane form) {
        BindingContext context = new BindingContext(propertyContainer);
        PropertyPane parametersPane = new PropertyPane(context);
        JPanel parametersPanel = parametersPane.createPanel();
        parametersPanel.setBorder(new EmptyBorder(2, 2, 2, 2));
        parametersPanel.setSize(3, 3);
        JScrollPane scrollPane = new JScrollPane(parametersPanel);
        form.add("Processing Parameters", scrollPane);
        parametersPane.getBindingContext().bindEnabledState("reprojectionCRS",false,"stayOnOlciGrid",true);
        parametersPane.getBindingContext().bindEnabledState("upsamplingMethod",false,"stayOnOlciGrid",true);
        parametersPane.getBindingContext().bindEnabledState("misrFile",true,"useMISR",true);
        parametersPane.getBindingContext().bindEnabledState("misrFile",false,"useMISR",false);
    }

    private void setSourceProductSelectorToolTipTexts() {
        for (Field field : sourceProductSelectorMap.keySet()) {
            final SourceProductSelector selector = sourceProductSelectorMap.get(field);
            final SourceProduct annot = field.getAnnotation(SourceProduct.class);
            final String description = annot.description();
            if (!description.isEmpty()) {
                selector.getProductNameComboBox().setToolTipText(description);
            }
        }
    }

    private void initSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.addSelectionChangeListener(new SelectionChangeListener() {
                @Override
                public void selectionChanged(SelectionChangeEvent selectionChangeEvent) {
                    setTargetProductName();
                }

                @Override
                public void selectionContextChanged(SelectionChangeEvent selectionChangeEvent) {
                }
            });
            sourceProductSelector.initProducts();
        }
    }

    private void setTargetProductName() {
        final TargetProductSelectorModel targetProductSelectorModel = targetProductSelector.getModel();
        HashMap<String, Product> sourceProductsMap = createSourceProductsMap();
        Product olciProduct = sourceProductsMap.get("olciProduct");
        Product slstrProduct = sourceProductsMap.get("slstrProduct");

        if (olciProduct != null && slstrProduct != null) {
            String synName = L1cSynUtils.getSynName(slstrProduct, olciProduct);
            targetProductSelectorModel.setProductName(synName);
        }
    }

    private void releaseSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.releaseProducts();
        }
    }

}