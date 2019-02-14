package org.esa.s3tbx.l1csyn.op.ui;

import org.esa.snap.rcp.actions.AbstractSnapAction;
import org.esa.snap.ui.AppContext;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;

@ActionID(category = "Preprocessing", id = "org.esa.s3tbx.l1csyn.op.ui.L1cSynAction" )
@ActionRegistration(displayName = "#CTL_L1cSynAction_Text")
@ActionReference(path = "Menu/Optical/Preprocessing", position = 0 )
@NbBundle.Messages({"CTL_L1cSynAction_Text=L1C SYN Tool 2"})

public class L1cSynAction extends AbstractSnapAction {
    private static final String OPERATOR_ALIAS = "L1CSYN";

    public L1cSynAction() {
        putValue(SHORT_DESCRIPTION, "Creates L1C Product from OLCI and SLSTR products");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final AppContext appContext = getAppContext();

        final L1cSynDialog dialog = new L1cSynDialog(OPERATOR_ALIAS, appContext, "L1c Synergy Tool", "L1cSynTool", "_L1cSyn");


        dialog.setTargetProductNameSuffix("_L1CSYN");
        dialog.getJDialog().pack();
        dialog.show();
    }

}