package org.omnetpp.simulation.inspectors.actions;

import org.omnetpp.simulation.SimulationPlugin;
import org.omnetpp.simulation.SimulationUIConstants;
import org.omnetpp.simulation.canvas.IInspectorContainer;
import org.omnetpp.simulation.model.cComponent;

/**
 * 
 * @author Andras
 */
public class ModelInformationAction extends AbstractInspectorAction {
    public ModelInformationAction() {
        super("Model information", AS_PUSH_BUTTON, SimulationPlugin.getImageDescriptor(SimulationUIConstants.IMG_TOOL_INFO));
    }

    @Override
    public void run() {
        cComponent component = (cComponent) getInspectorPart().getObject();
        IInspectorContainer container = getInspectorContainer();
        //TODO...
    }

    @Override
    public void update() {
        setEnabled(getInspectorPart() != null);
    }
}
