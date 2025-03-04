/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.test.gui.access;

import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.gef.ConnectionEditPart;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.RootEditPart;
import org.eclipse.gef.ui.palette.FlyoutPaletteComposite;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Text;
import org.omnetpp.common.util.IPredicate;
import org.omnetpp.common.util.ReflectionUtils;
import org.omnetpp.figures.ConnectionFigure;
import org.omnetpp.ned.editor.graph.GraphicalNedEditor;
import org.omnetpp.ned.editor.graph.parts.ModuleConnectionEditPart;
import org.omnetpp.ned.editor.graph.parts.NedEditPart;
import org.omnetpp.ned.model.INedElement;
import org.omnetpp.ned.model.pojo.CompoundModuleElement;
import org.omnetpp.ned.model.pojo.SimpleModuleElement;

import com.simulcraft.test.gui.access.Access;
import com.simulcraft.test.gui.access.EditPartAccess;
import com.simulcraft.test.gui.access.EditorPartAccess;
import com.simulcraft.test.gui.access.FigureAccess;
import com.simulcraft.test.gui.access.FlyoutPaletteCompositeAccess;
import com.simulcraft.test.gui.access.TextAccess;
import com.simulcraft.test.gui.core.InBackgroundThread;
import com.simulcraft.test.gui.core.UIStep;


public class GraphicalNedEditorAccess
    extends EditorPartAccess
{
    private final static String NBSP = "\u00A0";
    public GraphicalNedEditorAccess(GraphicalNedEditor editorPart) {
        super(editorPart);
    }

    @Override
    public GraphicalNedEditor getWorkbenchPart() {
        return (GraphicalNedEditor)workbenchPart;
    }

    public FlyoutPaletteCompositeAccess getFlyoutPaletteComposite() {
        return new FlyoutPaletteCompositeAccess((FlyoutPaletteComposite)ReflectionUtils.getFieldValue(getWorkbenchPart(), "splitter"));
    }

    @InBackgroundThread
    public void createSimpleModuleWithPalette(String name) {
        createElementWithPalette("Simple"+NBSP+"Module", name);
    }

    @InBackgroundThread
    public void createModuleInterfaceWithPalette(String name) {
        createElementWithPalette("Module"+NBSP+"Interface", name);
    }

    @InBackgroundThread
    public void createChannelWithPalette(String name) {
        createElementWithPalette("Channel", name);
    }

    @InBackgroundThread
    public void createChannelInterfaceWithPalette(String name) {
        createElementWithPalette("Channel"+NBSP+"Interface", name);
    }

    @InBackgroundThread
    public void renameElement(String oldName, String newName) {
        clickLabelFigure(oldName);
        typeInNewName(newName);
    }

    @UIStep
    private void typeInNewName(String name) {
        FigureCanvas figureCanvas = getWorkbenchPart().getFigureCanvas();
        Text text = (Text)Access.findDescendantControl(figureCanvas, Text.class);
        TextAccess textAccess = new TextAccess(text);
        textAccess.pressKey(SWT.F6);
        textAccess.typeIn(name);
        textAccess.pressEnter();
    }

    @UIStep
    public void clickLabelFigure(final String name) {
        IFigure labelFigure = findDescendantFigure(getRootFigure(), new IPredicate() {
            public boolean matches(Object object) {
                return object instanceof Label && ((Label)object).getText().matches(name);
            }
        });

        new FigureAccess(labelFigure).click(LEFT_MOUSE_BUTTON);
    }

    @SuppressWarnings("unchecked")
    @UIStep
    public void clickConnectionFigure(final String label1, final String label2) {
        final IPredicate predicate = new IPredicate() {
            public boolean matches(Object object) {
                if (object instanceof ModuleConnectionEditPart) {
                    ModuleConnectionEditPart connectionEditPart = (ModuleConnectionEditPart)object;
                    String sourceName = ((INedElement)connectionEditPart.getSource().getModel()).getAttribute("name");
                    String targetName = ((INedElement)connectionEditPart.getTarget().getModel()).getAttribute("name");

                    return sourceName.matches(label1) && targetName.matches(label2);
                }

                return false;
            }
        };
        GraphicalEditPart graphicalEditPart = (GraphicalEditPart)findDescendantEditPart(getRootEditPart(), new IPredicate() {
            @SuppressWarnings("unchecked")
            public boolean matches(Object object) {
                if (object instanceof GraphicalEditPart) {
                    GraphicalEditPart graphicalEditPart = (GraphicalEditPart)object;
                    return hasObject(graphicalEditPart.getSourceConnections(), predicate);
                }

                return false;
            }
        });

        ConnectionEditPart connectionEditPart = (ConnectionEditPart)findObject(graphicalEditPart.getSourceConnections(), predicate);
        ConnectionFigure connectionFigure = (ConnectionFigure)connectionEditPart.getFigure();
        new FigureAccess(connectionFigure).click(LEFT_MOUSE_BUTTON);
    }

    @UIStep
    public void clickBackground() {
        getComposite().clickAbsolute(LEFT_MOUSE_BUTTON, getCompositeInternal().toDisplay(1, 1));
    }

    @UIStep
    public void clickPaletteItem(String label) {
        getFlyoutPaletteComposite().clickButtonFigureWithLabel(label);
    }

    @InBackgroundThread
    public CompoundModuleEditPartAccess createCompoundModuleWithPalette(String name) {
        clickPaletteItem("Compound"+NBSP+"Module");
        clickBackground();
        renameElement("Unnamed.*", name);

        return findCompoundModule(name);
    }



    @UIStep
    public EditPartAccess findModule(final String name, final Class<?> type) {
        return (EditPartAccess)createAccess((EditPart)findDescendantEditPart(getRootEditPart(), new IPredicate() {
            public boolean matches(Object object) {
                if (object instanceof NedEditPart) {
                    INedElement nedElement = ((NedEditPart)object).getNedModel();

                    return type.isInstance(nedElement) && nedElement.getAttribute("name").matches(name);
                }
                else
                    return false;
            }
        }));
    }

    public EditPartAccess findSimpleModule(String name) {
        return findModule(name, SimpleModuleElement.class);
    }

    public CompoundModuleEditPartAccess findCompoundModule(String name) {
        return (CompoundModuleEditPartAccess)findModule(name, CompoundModuleElement.class);
    }

    private void createElementWithPalette(String elementTypeName, String elementName) {
        clickPaletteItem(elementTypeName);
        // TODO: inserting sleep(1); causes the palette button to be released even if clicked and thus fail to create the element
        clickBackground();

        if (elementName != null)
            renameElement("Unnamed.*", elementName);
    }

    private GraphicalViewer getGraphicalViewer() {
        GraphicalViewer graphicalViewer = (GraphicalViewer)ReflectionUtils.invokeMethod(getWorkbenchPart(), "getGraphicalViewer");
        return graphicalViewer;
    }

    private RootEditPart getRootEditPart() {
        return getGraphicalViewer().getRootEditPart();
    }

    private IFigure getRootFigure() {
        FigureCanvas figureCanvas = getWorkbenchPart().getFigureCanvas();
        IFigure rootFigure = figureCanvas.getLightweightSystem().getRootFigure();
        return rootFigure;
    }
}
