/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.test.gui.nededitor.main;


import junit.framework.Test;
import junit.framework.TestSuite;

public class MultipageNedEditorTestSuite
    extends TestSuite
{
    public MultipageNedEditorTestSuite() {
        addTestSuite(OpenFileTest.class);
        addTestSuite(SaveFileTest.class);
        addTestSuite(DeleteOpenFileTest.class);
        addTestSuite(PageChangeTest.class);
        addTestSuite(ExecuteUndoRedoUndoTestSuite.class);
    }

    public static Test suite() {
        return new MultipageNedEditorTestSuite();
    }
}
