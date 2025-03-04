/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.editors.datatable;

import static org.omnetpp.scave.model2.FilterField.MODULE;
import static org.omnetpp.scave.model2.FilterField.NAME;
import static org.omnetpp.scave.model2.FilterField.RUN;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.omnetpp.common.ui.FilterCombo;
import org.omnetpp.scave.ScavePlugin;
import org.omnetpp.scave.model2.Filter;
import org.omnetpp.scave.model2.FilterField;
import org.omnetpp.scave.model2.FilterHints;
import org.omnetpp.scave.model2.FilterUtil;

/**
 * A composite with UI elements to filter a data table.
 * This is a passive component, needs to be configured
 * to do anything useful.
 * @author andras
 */
public class FilteringPanel extends Composite {

    private final List<FilterField> simpleFilterFields = Collections.unmodifiableList(
                                                            Arrays.asList(new FilterField[] {RUN, MODULE, NAME}));

    private Image IMG_BASICFILTER = ScavePlugin.getImage("icons/full/obj16/basicfilter.png");
    private Image IMG_ADVANCEDFILTER = ScavePlugin.getImage("icons/full/obj16/advancedfilter.png");
    private Image IMG_RUNFILTER = ScavePlugin.getImage("icons/full/obj16/runfilter.png");

    // Switch between "Simple" and "Advanced"
    private Button toggleFilterTypeButton;
    private boolean showingAdvancedFilter;
    private StackLayout stackLayout;  // to set topControl to either advancedFilterPanel or simpleFilterPanel

    // Edit field for the "Advanced" mode
    private Composite advancedFilterPanel;
    private org.omnetpp.scave.editors.datatable.FilterField advancedFilter; // TODO rename FilterField to avoid collision

    // Combo boxes for the "Simple" mode
    private Composite simpleFilterPanel;
    private Combo runCombo;
    private Combo moduleCombo;
    private Combo dataCombo;

    // The "Go" button
    private Button filterButton;

    public FilteringPanel(Composite parent, int style) {
        super(parent, style);
        initialize();
    }

    public Text getAdvancedFilterText() {
        return advancedFilter.getText();
    }

    public List<FilterField> getSimpleFilterFields()
    {
        return simpleFilterFields;
    }

    public Combo getModuleNameCombo() {
        return moduleCombo;
    }

    public Combo getNameCombo() {
        return dataCombo;
    }

    public Combo getRunNameCombo() {
        return runCombo;
    }

    public Combo getFilterCombo(FilterField field)
    {
        if (field.equals(RUN))
            return runCombo;
        else if (field.equals(MODULE))
            return moduleCombo;
        else if (field.equals(NAME))
            return dataCombo;
        else
            return null;
    }

    public Button getFilterButton() {
        return filterButton;
    }

    public Button getToggleFilterTypeButton() {
        return toggleFilterTypeButton;
    }

    public void setFilterHints(FilterHints hints) {
        setFilterHints(runCombo, hints.getHints(RUN));
        setFilterHints(moduleCombo, hints.getHints(MODULE));
        setFilterHints(dataCombo, hints.getHints(NAME));
        advancedFilter.setFilterHints(hints);
    }

    public void setFilterHintsOfCombos(FilterHints hints)
    {
        for (FilterField field : hints.getFields())
        {
            Combo combo = getFilterCombo(field);
            if (combo != null)
                setFilterHints(combo, hints.getHints(field));
        }
    }

    private void setFilterHints(Combo filterCombo, String[] hints) {
        String text = filterCombo.getText();

        String[] items = hints;
        // prevent gtk halting when the item count ~10000
        int maxCount = 1000;
        if (hints.length > maxCount) {
            items = new String[maxCount];
            System.arraycopy(hints, 0, items, 0, maxCount - 1);
            items[maxCount - 1] = String.format("<%d skipped>", hints.length - (maxCount - 1));
        }
        filterCombo.setItems(items);
        int index = filterCombo.indexOf(text);
        if (index >= 0)
            filterCombo.select(index);
    }

    public void showSimpleFilter() {
        stackLayout.topControl = simpleFilterPanel;
        showingAdvancedFilter = false;
        toggleFilterTypeButton.setImage(IMG_ADVANCEDFILTER);
        toggleFilterTypeButton.setToolTipText("Switch to Advanced Filter");
        getParent().layout(true, true);
    }

    public void showAdvancedFilter() {
        stackLayout.topControl = advancedFilterPanel;
        showingAdvancedFilter = true;
        toggleFilterTypeButton.setImage(IMG_BASICFILTER);
        toggleFilterTypeButton.setToolTipText("Switch to Basic Filter");
        getParent().layout(true, true);
    }

    /**
     * Switches the filter from "Advanced" to "Basic" mode. If this cannot be done
     * (filter string invalid or too complex), the user is prompted with a dialog,
     * and switching may or may not actually take place depending on the answer.
     * @return true if switching was actually done.
     */
    public boolean trySwitchToSimpleFilter() {
        if (!isFilterPatternValid()) {
            MessageDialog.openWarning(getShell(), "Error in Filter Expression", "Filter expression is invalid, please fix it first. (Or, just delete the whole text.)");
            return false;
        }

        String filterPattern = getAdvancedFilterText().getText();
        FilterUtil filterUtil = new FilterUtil(filterPattern, true);
        if (filterUtil.isLossy()) {
            boolean ok = MessageDialog.openConfirm(getShell(), "Filter Too Complex", "The current filter cannot be represented in Basic view without losing some of its details.");
            if (!ok)
                return false;  // user cancelled
        }

        String[] supportedFields = new String[] {RUN.getName(), MODULE.getName(), NAME.getName()};
        if (!filterUtil.containsOnly(supportedFields)) {
            boolean ok = MessageDialog.openConfirm(getShell(), "Filter Too Complex", "The current filter contains fields not present in Basic view. These extra fields will be discarded.");
            if (!ok)
                return false;  // user cancelled
        }

        runCombo.setText(filterUtil.getField(RUN.getName()));
        moduleCombo.setText(filterUtil.getField(MODULE.getName()));
        dataCombo.setText(filterUtil.getField(NAME.getName()));

        showSimpleFilter();
        return true;
    }

    public void switchToAdvancedFilter() {
        getAdvancedFilterText().setText(assembleFilterPattern(getSimpleFilterFields()));
        showAdvancedFilter();
    }

    public boolean isShowingAdvancedFilter() {
        return showingAdvancedFilter;
    }

    public boolean isFilterPatternValid() {
        return getFilter().isValid();
    }

    public Filter getFilter() {
        String filterPattern;
        if (isShowingAdvancedFilter())
            filterPattern = getAdvancedFilterText().getText();
        else
            filterPattern = assembleFilterPattern(getSimpleFilterFields());
        return new Filter(filterPattern);
    }

    public Filter getFilterIfValid() {
        Filter filter = getFilter();
        return filter.isValid() ? filter : null;
    }

    public Filter getSimpleFilter(FilterField... includedFields)
    {
        return getSimpleFilter(Arrays.asList(includedFields));
    }

    public Filter getSimpleFilter(List<FilterField> includedFields)
    {
        return new Filter(assembleFilterPattern(includedFields));
    }

    public Filter getSimpleFilterExcluding(FilterField... excludedFields)
    {
        return getSimpleFilterExcluding(Arrays.asList(excludedFields));
    }

    public Filter getSimpleFilterExcluding(List<FilterField> excludedFields)
    {
        return new Filter(assembleFilterPatternExcluding(excludedFields));
    }

    private String assembleFilterPattern(List<FilterField> includedFields) {
        FilterUtil filter = new FilterUtil();
        for (FilterField field : simpleFilterFields)
        {
            if (includedFields.contains(field))
                filter.setField(field.getName(), getFilterCombo(field).getText());
        }
        String filterPattern = filter.getFilterPattern();
        return filterPattern.equals("*") ? "" : filterPattern;  // replace "*": "" also works, and lets the filter field show the hint text
    }

    private String assembleFilterPatternExcluding(List<FilterField> excludedFields) {
        FilterUtil filter = new FilterUtil();
        for (FilterField field : simpleFilterFields)
        {
            if (!excludedFields.contains(field))
                filter.setField(field.getName(), getFilterCombo(field).getText());
        }
        String filterPattern = filter.getFilterPattern();
        return filterPattern.equals("*") ? "" : filterPattern;  // replace "*": "" also works, and lets the filter field show the hint text
    }

    private void initialize() {
        GridLayout gridLayout;

        gridLayout = new GridLayout();
        gridLayout.marginHeight = 0;
        this.setLayout(gridLayout);

        Composite filterContainer = new Composite(this, SWT.NONE);
        filterContainer.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
        gridLayout = new GridLayout(3, false); // filter panel, [ExecuteFilter], [Basic/Advanced]
        gridLayout.marginHeight = 0;
        gridLayout.marginWidth = 0;
        filterContainer.setLayout(gridLayout);

        Composite filterFieldsContainer = new Composite(filterContainer, SWT.NONE);
        filterFieldsContainer.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        filterFieldsContainer.setLayout(stackLayout = new StackLayout());

        // the "Advanced" view with the content-assisted input field
        advancedFilterPanel = new Composite(filterFieldsContainer, SWT.NONE);
        advancedFilterPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        advancedFilterPanel.setLayout(new GridLayout(1, false));
        ((GridLayout)advancedFilterPanel.getLayout()).marginHeight = 1;
        ((GridLayout)advancedFilterPanel.getLayout()).marginWidth = 0;

        advancedFilter = new org.omnetpp.scave.editors.datatable.FilterField(advancedFilterPanel, SWT.SINGLE | SWT.BORDER | SWT.SEARCH);
        advancedFilter.getLayoutControl().setLayoutData(new GridData(SWT.FILL, SWT.END, true, true));
        advancedFilter.getText().setMessage("type filter expression");
        advancedFilter.getText().setToolTipText("Filter Expression (Ctrl+Space for Content Assist)");

        // the "Basic" view with a series of combo boxes
        simpleFilterPanel = new Composite(filterFieldsContainer, SWT.NONE);
        simpleFilterPanel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        simpleFilterPanel.setLayout(new GridLayout(1, false));
        ((GridLayout)simpleFilterPanel.getLayout()).marginHeight = 0;
        ((GridLayout)simpleFilterPanel.getLayout()).marginWidth = 0;

        Composite sashForm = new SashForm(simpleFilterPanel, SWT.SMOOTH);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.END, true, true));
        runCombo = createFilterCombo(sashForm, "runID filter", "RunID Filter");
        moduleCombo = createFilterCombo(sashForm, "module filter", "Module Filter");
        dataCombo = createFilterCombo(sashForm, "statistic name filter", "Statistic Name Filter");

        // Filter button
        filterButton = new Button(filterContainer, SWT.NONE);
        filterButton.setImage(IMG_RUNFILTER);
        filterButton.setToolTipText("Execute Filter");
        filterButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

        // Toggle button
        toggleFilterTypeButton = new Button(filterContainer, SWT.PUSH);
        toggleFilterTypeButton.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

        showSimpleFilter();
    }

    private Combo createFilterCombo(Composite parent, String filterMessage, String tooltipText) {
        FilterCombo combo = new FilterCombo(parent, SWT.BORDER);
        combo.setMessage(filterMessage);
        combo.setToolTipText(tooltipText);
        combo.setVisibleItemCount(20);

        return combo;
    }
}
