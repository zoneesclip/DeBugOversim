/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.test.gui.demo;

import java.io.IOException;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.omnetpp.common.CommonPlugin;
import org.omnetpp.common.eventlog.EventLogInput;
import org.omnetpp.common.util.PersistentResourcePropertyManager;
import org.omnetpp.eventlogtable.EventLogTablePlugin;
import org.omnetpp.eventlogtable.widgets.EventLogTable;
import org.omnetpp.scave.charting.VectorChart;
import org.omnetpp.sequencechart.SequenceChartPlugin;
import org.omnetpp.sequencechart.widgets.SequenceChart;
import org.omnetpp.test.gui.access.BrowseDataPageAccess;
import org.omnetpp.test.gui.access.ChartCanvasAccess;
import org.omnetpp.test.gui.access.CompoundModuleEditPartAccess;
import org.omnetpp.test.gui.access.GraphicalNedEditorAccess;
import org.omnetpp.test.gui.access.InifileEditorAccess;
import org.omnetpp.test.gui.access.InputsPageAccess;
import org.omnetpp.test.gui.access.NedEditorAccess;
import org.omnetpp.test.gui.access.ScaveEditorAccess;
import org.omnetpp.test.gui.access.SequenceChartAccess;
import org.omnetpp.test.gui.scave.ScaveEditorUtils;

import com.simulcraft.test.gui.access.Access;
import com.simulcraft.test.gui.access.ButtonAccess;
import com.simulcraft.test.gui.access.ComboAccess;
import com.simulcraft.test.gui.access.CompositeAccess;
import com.simulcraft.test.gui.access.EditorPartAccess;
import com.simulcraft.test.gui.access.ShellAccess;
import com.simulcraft.test.gui.access.StyledTextAccess;
import com.simulcraft.test.gui.access.TableAccess;
import com.simulcraft.test.gui.access.TableItemAccess;
import com.simulcraft.test.gui.access.TextAccess;
import com.simulcraft.test.gui.access.ToolItemAccess;
import com.simulcraft.test.gui.access.TreeAccess;
import com.simulcraft.test.gui.access.TreeItemAccess;
import com.simulcraft.test.gui.access.WorkbenchWindowAccess;
import com.simulcraft.test.gui.core.AnimationEffects;
import com.simulcraft.test.gui.core.GUITestCase;
import com.simulcraft.test.gui.core.UIStep;
import com.simulcraft.test.gui.util.WorkbenchUtils;
import com.simulcraft.test.gui.util.WorkspaceUtils;

public class Demo extends GUITestCase {
    private static String RECORDER = "C:\\Program Files\\TechSmith\\Camtasia Studio 4\\CamRecorder.exe" ;
    protected String name = "demo";
    protected String logFileName = name + "-0.elog";
    protected boolean delay = true;
    protected int readingMillisecPerWord = 300; // time to read a word, in milliseconds

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        WorkbenchWindowAccess workbenchWindow = Access.getWorkbenchWindow();
        workbenchWindow.closeAllEditorPartsWithHotKey();
        workbenchWindow.assertNoOpenEditorParts();
        System.clearProperty("com.simulcraft.test.running");
        setWorkbenchSize();
//        setupPreferences();
        WorkspaceUtils.ensureProjectNotExists(name);
//        sleep(12);
        sleep(1);
//        setTimeScale(0.3);
//        setMouseMoveDuration(1000);
//        setTimeScale(0.0);
//        setDelayAfterMouseMove(0);
//        setDelayBeforeMouseMove(0);
//        setMouseClickAnimation(false);
        readingMillisecPerWord = 10; // fast

    }

    public void testPlay() throws Throwable {
//        startRecording();
        sleep(2);
        welcome();
        openPerspective();
        createProject();
        createNedFile();
        createDemoNetwork();
        createIniFile();
        createLaunchConfigAndLaunch();
        analyzeResults();
        showSequenceChart();
        goodBye();
//        stopRecording();
        sleep(3);
    }

    @UIStep
    private void setWorkbenchSize() {
        Access.getWorkbenchWindow().getShell().getControl().setLocation(0, 0);
        Access.getWorkbenchWindow().getShell().getControl().setSize(988, 704);
    }

    private void setupPreferences() {
        Access.getWorkbenchWindow().getShell().chooseFromMainMenu("Window|Preferences.*");
        ShellAccess shell = Access.findShellWithTitle("Preferences");
        TreeAccess tree = shell.findTree();
        tree.findTreeItemByContent("Run/Debug").ensureExpanded();
        tree.findTreeItemByContent("Console").click();
        shell.findButtonWithLabel("Show when program writes to standard out").ensureSelection(false);
        shell.findButtonWithLabel("Show when program writes to standard error").ensureSelection(false);
        shell.findButtonWithLabel("OK").selectWithMouseClick();
    }

    private void welcome() {
        showMessage(
                "<b>Welcome to the OMNeT++ 4.0 Demo!</b>\n" +
                "\n" +
                "This demo shows you how to create, configure, run " +
                "and analyze a simulation model in the OMNeT++ IDE.", 4);
    }

    private void openPerspective() {
        showMessage(
                "The OMNeT++ IDE is built on Eclipse. First of all, we select " +
                "the <b>Simulation Perspective</b>, which switches the Eclipse workbench " +
                "to a layout optimized for OMNeT++, and adds OMNeT++-specific " +
                "menu items.", 4);
        Access.getWorkbenchWindow().getShell().chooseFromMainMenu("Window|Open Perspective|Simulation");
    }

    private void createProject() throws Throwable {
        showMessage(
                "We create a new OMNeT++ simulation project, using the " +
                "<b>OMNeT++ Project wizard</b>. This project will hold the files " +
                "we work with.", 3);
        // create project
        Access.getWorkbenchWindow().chooseFromMainMenu("File|New\tAlt\\+Shift\\+N|OMNeT\\+\\+ Project...");
        ShellAccess shell = Access.findShellWithTitle("New OMNeT\\+\\+ Project");
        sleep(1);
        showMessage("Let's name the project \"Demo\".", 1);
        shell.findTextAfterLabel("Project name:").clickAndTypeOver(name);
        sleep(2);
        shell.findButtonWithLabel("Next >").selectWithMouseClick();
        shell.findTree().findTreeItemByContent("Empty project").click();
        sleep(1);
        shell.findButtonWithLabel("Finish").selectWithMouseClick();

        sleep(1);
        TreeAccess tree = Access.getWorkbenchWindow().findViewPartByTitle("Project Explorer").getComposite().findTree();
        TreeItemAccess treeItem = tree.findTreeItemByContent(name);
        treeItem.ensureExpanded();

        // set dependent projects
        showMessage(
                "In this demo, we'll create and simulate a <b>queueing network</b>. " +
                "We'll build the network from components already defined " +
                "in the \"queueinglib\" project, so we have to include it in our " +
                "project's dependencies. We can do so in the <b>Project Properties</b> " +
                "dialog.", 5);
        treeItem.reveal().chooseFromContextMenu("Properties\tAlt\\+Enter");
        ShellAccess shell2 = Access.findShellWithTitle("Properties for demo");
        TreeAccess tree2 = shell2.findTree();
        sleep(1);
        showMessage("We need to choose the Project References page, and check the \"queueinglib\" project.", 2);
        tree2.findTreeItemByContent("Project References").click();
        sleep(1);
        shell2.findTable().findTableItemByContent("queueinglib").ensureChecked(true);

        sleep(2);
        shell2.findButtonWithLabel("OK").selectWithMouseClick();

        sleep(3);
    }

    private void createNedFile() throws Throwable {
        showMessage(
                "The next step is to create a new NED file with an empty network, " +
                "using the <b>NED File wizard</b>.", 2);
        WorkspaceUtils.ensureFileNotExists("/demo/demo.ned");
        TreeAccess tree = Access.getWorkbenchWindow().findViewPartByTitle("Project Explorer").getComposite().findTree();
        tree.findTreeItemByContent(name).reveal().chooseFromContextMenu("New|Network Description File \\(NED\\)");
        sleep(1);
        ShellAccess shell = Access.findShellWithTitle("New NED File");
        shell.findTree().findTreeItemByContent(name).click();
        TextAccess text = shell.findTextAfterLabel("File name:");
        text.clickAndTypeOver(name);
        shell.findButtonWithLabel("Next >").selectWithMouseClick();

        showMessage("We'll start from an empty network.", 1);
        shell.findTree().findTreeItemByContent("Empty NED file").click();
        shell.findButtonWithLabel("Finish").selectWithMouseClick();
        sleep(2);
    }

    private void createDemoNetwork() {
        showMessage(
                "Let's build a <b>closed queuing network</b> with a single " +
                "source node, and three queues connected in a ring.", 2);
        NedEditorAccess editor = (NedEditorAccess)Access.getWorkbenchWindow().findEditorPartByTitle("demo\\.ned");
        GraphicalNedEditorAccess graphEd = editor.ensureActiveGraphicalEditor();

        CompoundModuleEditPartAccess compoundModule = graphEd.findCompoundModule("demo");

        showMessage("Module types are available from the palette, at the right side of the graphical editor.", 2, 100);
        compoundModule.createSubModuleWithPalette("Queue.*", "queue1", 180, 150);
        sleep(1);
        compoundModule.createSubModuleWithPalette("Queue.*", "queue2", 300, 210);
        sleep(1);
        compoundModule.createSubModuleWithPalette("Queue.*", "queue3", 300, 90);
        sleep(1);
        compoundModule.createSubModuleWithPalette("Source.*", null, 60, 150);

        showMessage("Now we connect the submodules with the Connection Tool.", 1, -50);
        compoundModule.createConnectionWithPalette("source", "queue1", ".*");
        showMessage("When creating a connection, we choose the gates to be connected from a popup menu.", 2, 100);
        compoundModule.createConnectionWithPalette("queue1", "queue2", ".*");
        sleep(1);
        compoundModule.createConnectionWithPalette("queue3", "queue1", ".*");
        sleep(1);

        showMessage("The network can be edited in text mode as well. Let's add the last connection in text mode.", 2, 100);
        StyledTextAccess styledText = editor.ensureActiveTextEditor().findStyledText();
        styledText.moveCursorAfter(".*queue1\\.in\\+\\+;");
        sleep(1);
        styledText.pressEnter();
        showMessage("We'll use <b>Content Assist</b> to write the code.", 1, 100);
        styledText.pressKey('q');
        styledText.pressKey(' ', SWT.CTRL);
        sleep(1);
        Access.findContentAssistPopup().chooseWithKeyboard("queue2 - submodule");
        styledText.pressKey('.');
        sleep(1);
        Access.findContentAssistPopup().chooseWithMouse("out - gate");
        sleep(1);
        styledText.typeIn(" --> q");
        sleep(1);
        styledText.pressKey(' ', SWT.CTRL);
        sleep(1);
        Access.findContentAssistPopup().chooseWithKeyboard("queue3 - submodule");
        sleep(1);
        styledText.typeIn(".in++;");
        sleep(3);

        editor.ensureActiveGraphicalEditor();
        sleep(3);

        Access.getWorkbenchWindow().getShell().findToolItemWithTooltip("Save.*").click();
    }

    private void createIniFile() throws Throwable {
        showMessage(
                "The network needs to be configured before it can be run. " +
                "Now we'll create an Ini file and set the model parameters there. " +
                "We'll use the <b>Ini File Wizard</b> to create the file.", 3);
        // create with wizard
        WorkspaceUtils.ensureFileNotExists("/demo/omnetpp.ini");
        WorkbenchWindowAccess workbenchWindow = Access.getWorkbenchWindow();
        TreeAccess tree = workbenchWindow.findViewPartByTitle("Project Explorer").getComposite().findTree();
        tree.findTreeItemByContent(name).reveal().chooseFromContextMenu("New|Initialization File \\(ini\\)");
        ShellAccess shell = Access.findShellWithTitle("New Ini File");
        showMessage("We choose the network to be simulated.", 1);
        shell.findComboAfterLabel("NED Network:").selectItem(name);
        sleep(1);
        shell.findButtonWithLabel("Finish").selectWithMouseClick();
        sleep(2);

        // add source parameters
        showMessage("This is a text file which can be edited in both text mode and using forms.", 2);
        InifileEditorAccess iniEditor = (InifileEditorAccess)workbenchWindow.findEditorPartByTitle("omnetpp\\.ini");
        iniEditor.activateTextEditor();
        sleep(1);
        iniEditor.activateFormEditor();
        sleep(1);

        showMessage("Our next task is to assign model parameters that do not have default values.", 2);
        CompositeAccess form = (CompositeAccess)iniEditor.getActivePageControl();
        ButtonAccess addKeysButton = form.findButtonWithLabel("Add.*");
        addKeysButton.selectWithMouseClick();
        ShellAccess shell2 = Access.findShellWithTitle("Add Inifile Keys");
        showMessage(
                "This dialog lists all unassigned parameters, and inserts them into " +
                "the file. First, we choose the interArrivalTime and the number of jobs " +
                "in the Source submodule.", 3);
        shell2.findButtonWithLabel("Deselect All").selectWithMouseClick();
        sleep(1);
        TableAccess table = shell2.findTable();
        TableItemAccess tableItem = table.findTableItemByContent("\\*\\*\\.source\\.interArrivalTime");
        tableItem.ensureChecked(true);
        sleep(0.5);
        tableItem = table.findTableItemByContent("\\*\\*\\.source\\.numJobs");
        tableItem.ensureChecked(true);
        sleep(2);
        shell2.findButtonWithLabel("OK").selectWithMouseClick();
        sleep(2);

        // we should use table item access here
        TreeAccess tree2 = form.findTreeAfterLabel("HINT: Drag the icons to change the order of entries\\.");
        showMessage(
                "InterArrivalTime will be set to 0, meaning that all jobs will " +
                "be immediately injected into the queuing network.", 2);
        tree2.findTreeItemByContent("\\*\\*\\.source\\.interArrivalTime").clickAndTypeOver(1, "0\n");
        sleep(1);
        showMessage(
                "We want to run the model in two configurations, once with 30 and then " +
                "with 60 initial jobs. A special syntax, \"${...}\", allows us to specify this " +
                "directly in the Ini file.", 3, -40);
        tree2.findTreeItemByContent("\\*\\*\\.source\\.numJobs").clickAndTypeOver(1, "${jobs=30,60}\n");
        sleep(2);

        // add queue parameters from dialog
        showMessage("Now we set the service time for all queues in the system.", 1);
        addKeysButton.selectWithMouseClick();
        sleep(2);
        ShellAccess shell3 = Access.findShellWithTitle("Add Inifile Keys");
        shell3.findButtonWithLabel("Parameter name only.*").selectWithMouseClick();
        sleep(1);
        showMessage("We want to set only parameters without default values defined in the NED files.", 2);
        shell3.findButtonWithLabel("Skip parameters that have a default value").selectWithMouseClick();
        sleep(1);
        shell3.findTable().findTableItemByContent("\\*\\*\\.serviceTime").ensureChecked(true);
        sleep(2);
        shell3.findButtonWithLabel("OK").selectWithMouseClick();
        sleep(2);

        // add queue parameter values in table
        showMessage(
                "We want to try the model with different queue service times as well: " +
                "exponential distribution with mean 1, 2 and 3, so we specify serviceTime " +
                "to be a running parameter as exponential(${serviceMean=1..3 step 1})", 4);
        TextAccess cellEditor = tree2.findTreeItemByContent("\\*\\*\\.serviceTime").activateCellEditor(1);
        sleep(2);
        cellEditor.typeOver("exp");
        cellEditor.pressKey(' ', SWT.CTRL);
        sleep(2);
        Access.findContentAssistPopup().chooseWithKeyboard("exponential.*continuous.*");
        cellEditor.pressKey(SWT.BS);
        cellEditor.pressKey(SWT.BS);
        cellEditor.pressKey(SWT.BS);
        cellEditor.pressKey(SWT.BS);
        cellEditor.pressKey(SWT.BS);
        cellEditor.typeIn("${serviceMean=1..3 step 1})\n");
        sleep(3);
        showMessage("We could also specify that we run each configuration several times " +
                "with different seeds, but iterating over numJobs and serviceTimes " +
                "already generated enough runs for this demo.", 4);

        // set event logging file
        showMessage("Turning on the log file generation will allow us to analyze " +
                "the interaction between the modules later.", 2);
        ((TreeAccess)form.findControlWithID("CategoryTree")).findTreeItemByContent("Output Files").click();
        sleep(2);
        TextAccess text = form.findTextAfterLabel("Eventlog file:");
        text.clickAndTypeOver(name+"-${runnumber}.log");
        sleep(2);

        // set simulation time limit
        showMessage("Specify how long each simulation has to run, in simulation time.", 2);
        ((TreeAccess)form.findControlWithID("CategoryTree")).findTreeItemByContent("General.*").click();
        sleep(2);
        text = form.findTextAfterLabel("Simulation.*");
        text.clickAndTypeOver("20000");

        // set simulation time limit
        ((TreeAccess)form.findControlWithID("CategoryTree")).findTreeItemByContent("Cmdenv.*").click();
        sleep(2);
        form.findLabel("Status frequency.*").click();
        form.findTextAfterLabel("Status frequency.*").typeIn("500");

        sleep(3);

        showMessage("Let's check the result in text mode, and save our new Ini file.", 2);
        iniEditor.activatePageEditor("Text");
        sleep(2);
        workbenchWindow.getShell().findToolItemWithTooltip("Save.*").click();
        sleep(2);
    }

    private void createLaunchConfigAndLaunch() {
        showMessage(
                "Now we have a network defined in a NED file and an " +
                "Ini file defining the open parameters for the system. " +
                "As a next step we want to run the simulation from the IDE. " +
                "We have to create a <b>Launch Configuration</b> to do this.", 4);
        Access.getWorkbenchWindow().getShell().chooseFromMainMenu("Run|Open Run Dialog.*");
        sleep(2);
        ShellAccess shell = Access.findShellWithTitle("Run");

        shell.findTree().findTreeItemByContent("OMNeT\\+\\+ Simulation").click();
        sleep(1);
        shell.findToolItemWithTooltip("New launch configuration").click();
        sleep(1);

        showMessage("The simulation program itself is already compiled, and can be found in the \"queueinglib\" project.", 2);
        CompositeAccess cTabFolder = shell.findCTabFolder();
        cTabFolder.findTextAfterLabel("Simulation Program:").click();
        shell.pressKey(SWT.TAB);
        shell.pressKey(' ');
        sleep(2);
        TreeAccess tree = Access.findShellWithTitle("Select Executable File").findTree();
        tree.pressKey(SWT.ARROW_RIGHT);
        tree.findTreeItemByContent("queueinglib\\.exe").doubleClick();

        showMessage("Select the Ini file to be used with the simulation.", 1);

        cTabFolder.findTextAfterLabel("Initialization file\\(s\\):").click();
        shell.pressKey(SWT.TAB);
        shell.pressKey(' ');
        sleep(2);
        TreeAccess tree2 = Access.findShellWithTitle("Select INI Files").findTree();
        tree2.findTreeItemByContent(name).click();
        tree2.pressKey(SWT.ARROW_RIGHT);
        sleep(1);
        tree2.findTreeItemByContent("omnetpp\\.ini").doubleClick();

        showMessage("An Ini file may contain several configurations. Ours contains only one, General, so we select that.", 2);

        ComboAccess combo = cTabFolder.findComboAfterLabel("Configuration name:");
        combo.selectItem("General.*");
        showMessage("We will use the command line-environment to run the simulation.", 2);
        shell.findButtonWithLabel("Command.*").selectWithMouseClick();

        showMessage("In the Ini file we have specified that the simulation should be run with " +
                "30 and 60 jobs and queue service times should be exponential with means 1, 2 and 3. " +
                "This would result a total of 6 runs. We want to run each of them, " +
                "so we specify * (all) as a run number.", 5, -50);
        shell.findTextAfterLabel("Run number.*").clickAndTypeOver("*");

        showMessage(
                "This is a Dual Core machine, so we will gain speed if more than one " +
                "simulation runs at the same time. We set the maximum number of " +
                "simulations running at the same time to 2.", 4,-50);
        shell.pressKey(SWT.TAB);
        shell.pressKey(SWT.DEL);
        shell.pressKey('2');
        showMessage("Now we are ready to execute our simulation batch, so we click <b>Run</b>.", 2);
        shell.findButtonWithLabel("Run").selectWithMouseClick();

        showMessage("And watch the progress of the simulation batch in the <b>Progress View</b>.", 2);
        WorkbenchUtils.ensureViewActivated("General", "Progress.*");

        WorkbenchUtils.waitUntilProgressViewContains("Running simulations.*", 30);
        //try { Thread.sleep(10000); } catch (InterruptedException e) {}
        WorkbenchUtils.waitUntilProgressViewNotContains("Running simulations.*", 120);

        showMessage(
                "<b>Simulations completed!</b> Note the files that have been " +
                "created in the project directory. Vec and sca files hold " +
                "statistics recorded by the simulation. Log files contain a record " +
                "of each message sending, textual debug messages and more, " +
                "and can be visualized on sequence charts.", 5);
    }

    private void analyzeResults() throws Throwable {
        showMessage("We can analyze the results now. Let's create a new <b>Analysis</b> using a wizard.", 2);
        // create with wizard
        WorkspaceUtils.ensureFileNotExists("/demo/demo.anf");
        WorkbenchWindowAccess workbenchWindow = Access.getWorkbenchWindow();
        workbenchWindow.getShell().chooseFromMainMenu("File|New\tAlt\\+Shift\\+N|Analysis File \\(anf\\)");
        ShellAccess shell1 = Access.findShellWithTitle("New Analysis File");
        shell1.findTree().findTreeItemByContent("demo").click();
        TextAccess text1 = shell1.findTextAfterLabel("File name:");
        text1.clickAndTypeOver("demo");
        shell1.findButtonWithLabel("Finish").selectWithMouseClick();

        // create an analysis file
        ScaveEditorAccess scaveEditor = ScaveEditorUtils.findScaveEditor("demo\\.anf");
        InputsPageAccess ip = scaveEditor.ensureInputsPageActive();
        showMessage("First, add all generated result files to the analysis. " +
                "We could specify exact filenames or drag files from the Project Explorer, " +
                "but it is now much simpler to use wildcards.", 3);
        ip.findButtonWithLabel("Wildcard.*").selectWithMouseClick();
        Access.findShellWithTitle("Add files with wildcard").findButtonWithLabel("OK").selectWithMouseClick();

        ip.ensureFileRunViewVisible();
        showMessage("The editor lists the matching files below, one vector " +
                "and one scalar for each run. The files have actually not been " +
                "loaded into memory, only their contents were scanned.", 3);
        Access.sleep(2);

        TreeAccess runFileTree = ip.ensureRunFileViewVisible();
        runFileTree.findTreeItemByContent(".*General-0.*").ensureExpanded();
        showMessage("Each time a simulation is run, it receives a unique Run ID which " +
                "contains the configuration, run number, date/time, etc. The second " +
                "tab displays which files each run generated.", 4, -50);
        Access.sleep(2);

        TreeAccess logicalTree = ip.ensureLogicalViewVisible();
        showMessage("The third tab presents a logical grouping of the runs. " +
                "All runs we just did belong to one <b>experiment</b>, " +
                "named \"General\" -- the name of the Ini file configuration. " +
                "This default experiment name can be overridden in the Ini file.", 5, -80);
        logicalTree.findTreeItemByContent(".*General.*").ensureExpanded();
        Access.sleep(2);
        showMessage("Every experiment consists of several <b>measurements</b>, " +
                "which are usually the same simulation model being run with " +
                "different parameters.", 3);
        logicalTree.findTreeItemByContent(".*jobs.*30.*serviceMean.*2.*").ensureExpanded();
        Access.sleep(2);
        showMessage("Every measurement can be repeated with different seeds to gain " +
                "statistically trustworthy results, resulting in several " +
                "<b>replications</b>.", 3);
        logicalTree.findTreeItemByContent(".*replication.*").ensureExpanded();
        Access.sleep(2);
        showMessage("Each instance of running a replication receives a unique Run ID.", 2);
        Access.sleep(1);

        // browse data
        showMessage("We can now switch to the Data Browsing page.", 1);
        BrowseDataPageAccess bdp = scaveEditor.ensureBrowseDataPageActive();
        bdp.ensureVectorsSelected();
        Access.sleep(2);
        showMessage("The table displays vectors recorded in all simulation runs. " +
                "We are interested in how the queue lengths change over time, " +
                "so choose \"length\" from the filter combo.", 3, 50);
        bdp.getDataNameFilter().selectItem("length");
        Access.sleep(2);
        showMessage("The table still includes data from all runs, so let us focus on Run 4.", 2, 50);
        bdp.getRunNameFilter().selectItem("General-4.*");
        Access.sleep(2);

        showMessage("This is only three vectors, one for each queue. Let's plot them on a single chart.", 2, 50);
        bdp.click();
        bdp.pressKey('a', SWT.CTRL);
        workbenchWindow.getShell().findToolItemWithTooltip("Plot.*").click();

        // show and play with the chart
        CompositeAccess chartPage = (CompositeAccess)scaveEditor.ensureActivePage("Chart: temp1");
        ChartCanvasAccess chart = (ChartCanvasAccess)Access.createAccess(chartPage.findDescendantControl(VectorChart.class));
        sleep(3);

        // zoom out a rectangle
        //workbenchWindow.getShell().findToolItemWithTooltip("Zoom In").click();
        showMessage("Now we zoom in a little, and change plotting style to sample-hold.", 2);
        Rectangle r = chart.getViewportRectangle();
        chart.dragMouse(Access.LEFT_MOUSE_BUTTON, SWT.CTRL, r.x, r.y+r.height-1, r.x+30, r.y+50);

        chart.chooseFromContextMenu("Lines.*");
        ShellAccess dialog = Access.findShellWithTitle("Edit LineChart");
        TableAccess linesTable = dialog.findTableAfterLabel("Select line.*");
        linesTable.click();
        linesTable.pressKey('a', SWT.CTRL);
        dialog.findImageComboAfterLabel("Line type.*").selectItem("Sample-Hold");
        dialog.findImageComboAfterLabel("Symbol type.*").selectItem("None");
        dialog.findButtonWithLabel("Apply").selectWithMouseClick();
        dialog.findButtonWithLabel("OK").selectWithMouseClick();
        sleep(3);
        chart.dragMouse(Access.LEFT_MOUSE_BUTTON, SWT.CTRL, r.x, r.y+r.height-1, r.x+r.width/16, r.y+50);
        //workbenchWindow.getShell().findToolItemWithTooltip("Zoom In").click();
        chart.dragMouse(Access.LEFT_MOUSE_BUTTON, SWT.NONE, r.x+300, r.y+100, r.x+50, r.y+100);
        chart.dragMouse(Access.LEFT_MOUSE_BUTTON, SWT.NONE, r.x+300, r.y+100, r.x+50, r.y+100);
        sleep(3);

        showMessage("We can apply the \"mean\" function, to get a smoothed-out version of the chart.", 2);
        chart.chooseFromContextMenu("Apply|Mean");
        sleep(3);

        // create a dataset from the chart
        showMessage("We can store this chart as a recipe, so next time when the " +
                "simulation is re-run, the chart can be recreated automatically.", 3);
        chart.chooseFromContextMenu("Convert to Dataset.*");
        ShellAccess shell = Access.findShellWithTitle("Create chart template");
        sleep(2);
        shell.findTextAfterLabel("Dataset name:").typeOver("queue length mean");
        sleep(1);
        shell.findTextAfterLabel("Chart name:").clickAndTypeOver("queue length");
        sleep(1);
        shell.findButtonWithLabel("OK").selectWithMouseClick();

        // manipulate the dataset
        scaveEditor.ensureDatasetsPageActive();

        sleep(3);
        showMessage("The dataset page displays \"recipes\" used to create charts and diagrams. " +
                "It contains processing steps applied in top to bottom order. ", 3 ,100);
        sleep(2);
        showMessage("Let us save the analysis. The analysis file will only store " +
                "the \"recipe\": which files to load, and what datasets and charts " +
                "to draw from them.", 3, 100);

        workbenchWindow.getShell().findToolItemWithTooltip("Save.*").click();
        sleep(10);
    }

    private void showSequenceChart() {
        IResource resource = ResourcesPlugin.getWorkspace().getRoot().getProject(name).getFile(logFileName);
        new PersistentResourcePropertyManager(CommonPlugin.PLUGIN_ID).removeProperty(resource, EventLogInput.STATE_PROPERTY);
        new PersistentResourcePropertyManager(SequenceChartPlugin.PLUGIN_ID).removeProperty(resource, SequenceChart.STATE_PROPERTY);
        new PersistentResourcePropertyManager(EventLogTablePlugin.PLUGIN_ID).removeProperty(resource, EventLogTable.STATE_PROPERTY);

        WorkbenchWindowAccess workbenchWindow = Access.getWorkbenchWindow();
        ShellAccess workbenchShell = workbenchWindow.getShell();

        showMessage("Let's open one of the log files, and take a look at the <b>Sequence Chart</b>!", 2);

        TreeAccess tree = WorkbenchUtils.ensureViewActivated("General", "Project Explorer").getComposite().findTree();
        TreeItemAccess treeItem = tree.findTreeItemByContent(name);
        treeItem.ensureExpanded();
        tree.findTreeItemByContent(logFileName).doubleClick();

        showMessage("Here you can see the initial 60 messages being pushed into one of the queues.", 2);

        showMessage("Go to where the first message is first processed by a queue.", 1);

        EditorPartAccess editorPart = workbenchWindow.findEditorPartByTitle(logFileName);
        SequenceChartAccess sequenceChart = (SequenceChartAccess)Access.createAccess(Access.findDescendantControl(editorPart.getComposite().getControl(), SequenceChart.class));
        Rectangle r = sequenceChart.getAbsoluteBounds();
        sequenceChart.activateContextMenuWithMouseClick(2).activateMenuItemWithMouse("Sending.*cMessage.*").activateMenuItemWithMouse("Goto Consequence.*");

        showMessage("Zoom out to see more.", 1);

        ToolItemAccess toolItem = workbenchShell.findToolItemWithTooltip("Zoom Out");
        for (int i = 0; i < 5; i++)
            toolItem.click();

        showMessage("In non-linear timeline mode, messages in the same gray area have " +
                "the same simulation time. Now we switch to linear timeline mode and see the " +
                "initial messages being sent at zero simulation time (note that gray areas " +
                "will collapse to a single vertical line).", 5, 200);

        toolItem = workbenchShell.findToolItemWithTooltip("Timeline Mode");
        toolItem.activateDropDownMenu().activateMenuItemWithMouse("Linear");

        sequenceChart.dragMouse(Access.LEFT_MOUSE_BUTTON, SWT.NONE, 1, r.height / 2, r.width / 4, r.height / 2);

        showMessage("Filter for the first message to see how it circulates in the closed network...", 2);

        workbenchShell.findToolItemWithTooltip("Filter").click();
        ShellAccess filterShell = Access.findShellWithTitle("Filter event log");
        filterShell.findTree().findTreeItemByPath("Message filter/by name").ensureChecked(true);
        filterShell.findTable().findTableItemByContent("source 1").ensureChecked(true);
        filterShell.findButtonWithLabel("OK").selectWithMouseClick();

        sleep(5);
        showMessage("Switch to nonlinear mode to see the message going around several times.", 2);

        toolItem = workbenchShell.findToolItemWithTooltip("Timeline Mode");
        toolItem.activateDropDownMenu().activateMenuItemWithMouse("Nonlinear");

        toolItem = workbenchShell.findToolItemWithTooltip("Zoom Out");
        for (int i = 0; i < 3; i++)
            toolItem.click();

        sequenceChart.dragMouse(Access.LEFT_MOUSE_BUTTON, SWT.NONE, r.width - 1, r.height / 2, 1, r.height / 2);

        showMessage("Show where message objects are reused by resending them.", 2);

        sequenceChart.activateContextMenuWithMouseClick().activateMenuItemWithMouse("Show Reuse.*");

        sleep(2);

    }

    private void goodBye() {
        showMessage(
                "This concludes our demo. We suggest you continue exploring the OMNeT++ IDE " +
                "in your own installed copy, and gain first-hand experience.\n" +
                "\n" +
                "Have fun!", 5);
        sleep(3);
    }

    // **************************************************************************************
    // UTILITY functions
    void sleep(double time) {
        Access.sleep(time);
    }

    void showMessage(String msg, int lines) {
        showMessage(msg, lines, 0);
    }

    void showMessage(String msg, int lines, int verticalDisplacement) {
        if (readingMillisecPerWord == 0)
            return;

        //int width = 600;
        //int height = lines * 24 + 30;
        int width = 800;
        int height = lines * 30 + 50;
        Rectangle bounds = Access.getWorkbenchWindow().getShell().getAbsoluteBounds();
        int x = bounds.x + bounds.width/2 - width/2;
        int y = bounds.y + bounds.height/3;
        msg = msg.replace("\n", "<br/>");
        if (delay) {
            int numWords = msg.split("[^A-Za-z0-9]+").length;
            int delayMillis = 2000 + Access.rescaleTime(numWords*readingMillisecPerWord);
            AnimationEffects.showMessage(msg, x, y + verticalDisplacement, width, height, delayMillis);
        }
    }

    private void startRecording() {
        try {
            Runtime.getRuntime().exec(new String[] {RECORDER, "/r"}, null);
        }
        catch (IOException e) {
            System.err.println("Recorder cannot be started");
        }
    }

    private void stopRecording() {
        try {
            Runtime.getRuntime().exec(new String[] {RECORDER, "/s"}, null);
        }
        catch (IOException e) {
            System.err.println("Recorder cannot be stopped");
        }
    }

}
