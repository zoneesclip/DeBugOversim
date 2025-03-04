/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.test.gui.inifileeditor.parameterspage;

import org.omnetpp.test.gui.inifileeditor.InifileEditorTestCase;

import com.simulcraft.test.gui.access.CompositeAccess;
import com.simulcraft.test.gui.access.TreeAccess;

public class EditParameterTest extends InifileEditorTestCase {
    private void prepareTest(String content) throws Exception {
        createFileWithContent(content);
        openFileFromProjectExplorerView();
    }

    private void assertTextEditorContentMatches(String content) {
        findInifileEditor().ensureActiveTextEditor().assertContentIgnoringWhiteSpace(content);
    }

    public void testEditParameterValueAndContent() throws Exception {
        prepareTest(
                "[General]\n" +
                "**.par1 = 100 # one hundred\n");
        CompositeAccess parametersPage = findInifileEditor().ensureActiveFormPage("Parameters");
        TreeAccess tree = parametersPage.findTree();
        tree.findTreeItemByContent(".*par1.*").clickAndTypeOver(1, "200");
        tree.findTreeItemByContent(".*par1.*").clickAndTypeOver(2, "two hundred");

        assertTextEditorContentMatches(
                "[General]\n" +
                "**.par1 = 200 # two hundred\n");
    }

    public void testRenameParameter() throws Exception {
        prepareTest(
                "[General]\n" +
                "**.par1 = 100 # one hundred\n");
        CompositeAccess parametersPage = findInifileEditor().ensureActiveFormPage("Parameters");
        TreeAccess tree = parametersPage.findTree();
        tree.findTreeItemByContent(".*par1.*").clickAndTypeOver(0, "**.renamed");

        assertTextEditorContentMatches(
                "[General]\n" +
                "**.renamed = 100 # one hundred\n");
    }

    public void testChangeAllColumnsLeftToRight() throws Exception {
        prepareTest(
                "[General]\n" +
                "**.par1 = 100 # one hundred\n");
        CompositeAccess parametersPage = findInifileEditor().ensureActiveFormPage("Parameters");
        TreeAccess tree = parametersPage.findTree();
        tree.findTreeItemByContent(".*par1").clickAndTypeOver(0, "**.renamed");
        tree.findTreeItemByContent(".*renamed").clickAndTypeOver(1, "200");
        tree.findTreeItemByContent(".*renamed").clickAndTypeOver(2, "two hundred");

        assertTextEditorContentMatches(
                "[General]\n" +
                "**.renamed = 200 # two hundred\n");
    }

    public void testChangeAllColumnsRightToLeft() throws Exception {
        prepareTest(
                "[General]\n" +
                "**.par1 = 100 # one hundred\n");
        CompositeAccess parametersPage = findInifileEditor().ensureActiveFormPage("Parameters");
        TreeAccess tree = parametersPage.findTree();
        tree.findTreeItemByContent(".*par1").clickAndTypeOver(2, "two hundred");
        tree.findTreeItemByContent(".*par1").clickAndTypeOver(1, "200");
        tree.findTreeItemByContent(".*par1").clickAndTypeOver(0, "**.renamed");

        assertTextEditorContentMatches(
                "[General]\n" +
                "**.renamed = 200 # two hundred\n");
    }

    public void testClearValueAndComment() throws Exception {
        prepareTest(
                "[General]\n" +
                "**.par1 = 100 # one hundred\n");
        CompositeAccess parametersPage = findInifileEditor().ensureActiveFormPage("Parameters");
        TreeAccess tree = parametersPage.findTree();
        tree.findTreeItemByContent(".*par1").clickAndTypeOver(1, "");
        tree.findTreeItemByContent(".*par1").clickAndTypeOver(2, "");

        assertTextEditorContentMatches(
                "[General]\n" +
                "**.par1 =\n");
    }

    //TODO more tests:
    //  validation, content assist proposals

}
