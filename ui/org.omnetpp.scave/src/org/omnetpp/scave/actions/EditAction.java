/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.actions;

import static org.omnetpp.common.image.ImageFactory.TOOLBAR_IMAGE_PROPERTIES;
import static org.omnetpp.scave.editors.forms.IScaveObjectEditForm.PARAM_SELECTED_OBJECT;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;
import org.omnetpp.common.image.ImageFactory;
import org.omnetpp.scave.editors.ScaveEditor;
import org.omnetpp.scave.editors.ui.EditDialog;
import org.omnetpp.scave.engine.ResultFileManager;

/**
 * Opens an edit dialog for the selected dataset, chart, chart sheet, etc.
 */
public class EditAction extends AbstractScaveAction {
    private Map<String,Object> formParameters = null;

    /**
     * Creates the action with an default title and icon, and without parameters.
     */
    public EditAction() {
        setText("Properties...");
        setToolTipText("Edit the properties of the selected item");
        setImageDescriptor(ImageFactory.global().getDescriptor(TOOLBAR_IMAGE_PROPERTIES));
    }

    /**
     * Allows passing parameters to the action; e.g. for opening the Properties dialog
     * with a specific page selected, use the the following parameter:
     * formParameters.put(ChartEditForm.PROP_DEFAULT_TAB, ChartEditForm.TAB_MAIN);
     */
    public EditAction(String text, Map<String,Object> formParameters) {
        setText(text);
        setToolTipText("Edit the properties of the selected item");
        this.formParameters = formParameters;
    }

    public void setFormParameter(String paramName, Object paramValue) {
        if (formParameters == null)
            formParameters = new HashMap<String,Object>();
        formParameters.put(paramName, paramValue);
    }

    @Override
    protected void doRun(final ScaveEditor scaveEditor, final IStructuredSelection selection) {
        ResultFileManager.callWithReadLock(scaveEditor.getResultFileManager(), new Callable<Object>() {
            public Object call() throws Exception {
                if (isApplicable(scaveEditor, selection)) {
                    EObject editedObject = getEditedObject(scaveEditor, selection);
                    Object selectedObject = selection.getFirstElement();
                    setFormParameter(PARAM_SELECTED_OBJECT, selectedObject);
                    EditDialog dialog = new EditDialog(scaveEditor.getSite().getShell(), editedObject, scaveEditor, formParameters);
                    EStructuralFeature[] features = dialog.getFeatures();
                    if (features.length > 0)
                        dialog.open();
                }
                return null;
            }
        });
    }

    @Override
    public boolean isApplicable(final ScaveEditor editor, final IStructuredSelection selection) {
        return ResultFileManager.callWithReadLock(editor.getResultFileManager(), new Callable<Boolean>() {
            public Boolean call() throws Exception {
                return getEditedObject(editor, selection) != null;
            }
        });
    }

     //TODO edit several objects together?
    private EObject getEditedObject(ScaveEditor editor, IStructuredSelection selection) {
        if (selection.size() == 1) {
            EObject editedObject = null;
            if (selection.getFirstElement() instanceof EObject) {
                editedObject = (EObject)selection.getFirstElement();
            }
            else if (selection instanceof ITreeSelection) {
                ITreeSelection treeSelection = (ITreeSelection)selection;
                TreePath[] paths = treeSelection.getPaths();
                if (paths.length > 0) {
                    TreePath path = paths[0];
                    for (int i = path.getSegmentCount() - 1; i >= 0; --i) {
                        Object segment = path.getSegment(i);
                        if (segment instanceof EObject) {
                            editedObject = (EObject)segment;
                            break;
                        }
                    }
                }
            }
            if (editedObject != null && editedObject.eResource() != null &&
                    EditDialog.getEditableFeatures(editedObject, editor).length > 0)
                return editedObject;
        }
        return null;
    }
}
