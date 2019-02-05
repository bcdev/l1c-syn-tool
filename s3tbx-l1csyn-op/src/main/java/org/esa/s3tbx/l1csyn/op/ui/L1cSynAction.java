package org.esa.s3tbx.l1csyn.op.ui;

import org.esa.snap.rcp.actions.AbstractSnapAction;
import org.esa.snap.ui.AppContext;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;

@ActionID(category = "Processing", id = "org.esa.s3tbx.l1csyn.op.ui.L1cSynAction" )
@ActionRegistration(displayName = "#CTL_L1cSynAction_Text")
@ActionReference(path = "Menu/Optical/Preprocessing", position = 200 )
@NbBundle.Messages({"CTL_L1cSynAction_Text="})

public class L1cSynAction extends AbstractSnapAction {

    private static final String OPERATOR_ALIAS = "L1cSyn";
    private static final String HELP_ID = "flhMciScientificTool";
    private static final String LOWER_BASELINE_BAND_NAME = "lowerBaselineBandName";
    private static final String UPPER_BASE_LINE_BAND_NAME = "upperBaselineBandName";
    private static final String SIGNAL_BAND_NAME = "signalBandName";

    public L1cSynAction() {
        putValue(SHORT_DESCRIPTION, "Generates florescence line height (FLH) / maximum chlorophyll index (MCI) from spectral bands.");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final AppContext appContext = getAppContext();

        //final DefaultSingleTargetProductDialog dialog = new DefaultSingleTargetProductDialog(OPERATOR_ALIAS, appContext,
         //       Bundle.CTL_FlhMciAction_Text(),
         //       HELP_ID);
        final L1cSynDialog dialog = new L1cSynDialog(OPERATOR_ALIAS, getAppContext(), "L1c Synergy Tool", "L1cSynTool", "_L1cSyn");
        //final BindingContext bindingContext = dialog.getBindingContext();
        //final PropertySet propertySet = bindingContext.getPropertySet();
        //configurePropertySet(propertySet);

        //bindingContext.bindEnabledState("slopeBandName", true, "slope", true);
        //bindingContext.addPropertyChangeListener("preset", new PropertyChangeListener() {
            /*
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                final Presets preset = (Presets) evt.getNewValue();
                if (preset != Presets.NONE) {
                    setValueIfValid(propertySet, LOWER_BASELINE_BAND_NAME, preset.getLowerBaselineBandName());
                    setValueIfValid(propertySet, UPPER_BASE_LINE_BAND_NAME, preset.getUpperBaselineBandName());
                    setValueIfValid(propertySet, SIGNAL_BAND_NAME, preset.getSignalBandName());
                    propertySet.setValue("lineHeightBandName", preset.getLineHeightBandName());
                    propertySet.setValue("slopeBandName", preset.getSlopeBandName());
                    propertySet.setValue("maskExpression", preset.getMaskExpression());
                }
            }

            private void setValueIfValid(PropertySet propertySet, String propertyName, String bandName) {
                if (propertySet.getDescriptor(propertyName).getValueSet().contains(bandName)) {
                    propertySet.setValue(propertyName, bandName);
                }
            }
        });

        dialog.setTargetProductNameSuffix("_flhmci");
        */

        dialog.setTargetProductNameSuffix("_L1CSYN");
        dialog.getJDialog().pack();
        dialog.show();
    }

}