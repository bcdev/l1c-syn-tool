package org.esa.s3tbx.l1csyn.op.ui;

import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertySet;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.BindingContext;
import com.bc.ceres.swing.binding.PropertyPane;
import org.esa.snap.core.dataio.ProductSubsetDef;
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

    private static List<SourceProductSelector> sourceProductSelectorList;
    private Map<Field, SourceProductSelector> sourceProductSelectorMap;
    //private static ProductSubsetDef subsetDef;
    private String operatorName;
    private static Map<String, Object> parameterMap;
    private JTabbedPane form;
    private String targetProductNameSuffix;
    private AppContext appContext;

    private static OperatorSpi operatorSpi;
    private String helpID;
    private HashMap<String, Object> paneMap;
     /*
     * DefaultDialog constructor
     */
    public L1cSynDialog(String operatorName, AppContext appContext, String title, String helpID, String targetProductNameSuffix) {
        super(appContext, title, helpID);
        this.helpID = helpID;
        this.operatorName = operatorName;
        this.appContext = appContext;
        this.targetProductNameSuffix = targetProductNameSuffix;
        System.setProperty("gpfMode", "GUI");
        initialize(operatorName);
    }

    @Override
    protected Product createTargetProduct()  {
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
        for (SourceProductSelector selector : sourceProductSelectorList) {
            ioParametersPanel.add(selector.createDefaultPanel());
        }
        ioParametersPanel.add(getTargetProductSelector().createDefaultPanel());
        ioParametersPanel.add(tableLayout.createVerticalSpacer());

        final TargetProductSelectorModel targetProductSelectorModel = getTargetProductSelector().getModel();

        targetProductSelectorModel.setFormatName("NetCDF4-CF");


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
                SourceProductSelector sourceProductSelector = new SourceProductSelector(appContext);
                if (field.getName().equals("olciSource")) {
                     sourceProductSelector = new SourceProductSelector(appContext, "OLCI PRODUCT",false);
                }
                else if (field.getName().equals("slstrSource")){
                     sourceProductSelector = new SourceProductSelector(appContext, "SLSTR PRODUCT",false);
                }
                sourceProductSelector.setProductFilter(productFilter);
                sourceProductSelectorList.add(sourceProductSelector);
                sourceProductSelectorMap.put(field, sourceProductSelector);
            }
        }
    }

     HashMap<String, Product> createSourceProductsMap() {
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
        parameterMap = new LinkedHashMap<>(17);

        ///
        form = new JTabbedPane();
        initComponents();
        final PropertyContainer propertyContainer =
                PropertyContainer.createMapBacked(parameterMap, operatorSpi.getOperatorClass(),
                        new ParameterDescriptorFactory());
        paneMap = new LinkedHashMap<>();
        addFormParameterPane(propertyContainer, "Processing Parameters", form);
        ///

    }

    private void addFormParameterPane(PropertyContainer propertyContainer, String title, JTabbedPane form) {
        BindingContext context = new BindingContext(propertyContainer);
        PropertyPane parametersPane = new PropertyPane(context);
        JPanel parametersPanel = parametersPane.createPanel();
        parametersPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        JButton button = new JButton("Regional Subsetting");
        button.addActionListener(new L1cSynSubsetAction(this));
        ///
        parametersPanel.add(button);
        JScrollPane scrollPane = new JScrollPane(parametersPanel);
        form.add(title,scrollPane );
        paneMap.put(title,scrollPane);
    }

    private void updateFormParameterPane(String title) {
        //BindingContext context = new BindingContext(propertyContainer);
        JScrollPane scrollPane = (JScrollPane) paneMap.get(title);
        scrollPane.updateUI();
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
            sourceProductSelector.initProducts();
        }
    }

    private void releaseSourceProductSelectors() {
        for (SourceProductSelector sourceProductSelector : sourceProductSelectorList) {
            sourceProductSelector.releaseProducts();
        }
    }

    public void setTargetProductNameSuffix(String suffix) {
        targetProductNameSuffix = suffix;
    }

      void setParameters(ProductSubsetDef subsetDef){
         OperatorParameterSupport parameterSupport = new OperatorParameterSupport(operatorSpi.getOperatorDescriptor());
         PropertySet propertyContainer = parameterSupport.getPropertySet();
         propertyContainer.setValue("upsampling", "Bilinear");
         propertyContainer.setValue("time",2L);
          

         //updateFormParameterPane("Processing Parameters");
         //this.subsetDef = subsetDef;
    }

     static Product[] getSourceProducts() {
        Product slstrSource = sourceProductSelectorList.get(0).getSelectedProduct();
        Product olciSource = sourceProductSelectorList.get(1).getSelectedProduct();
        Product[] products = {slstrSource,olciSource};
        return products ;
    }


}