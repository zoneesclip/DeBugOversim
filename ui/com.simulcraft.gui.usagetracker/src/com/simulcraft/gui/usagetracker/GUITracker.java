/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package com.simulcraft.gui.usagetracker;

import org.omnetpp.common.util.StringUtils;

import com.simulcraft.test.gui.recorder.GUIRecorder;
import com.simulcraft.test.gui.recorder.IGuiRecorderListener;
import com.simulcraft.test.gui.recorder.JavaSequence;


/**
 * Records user activities into the log, as Java code
 *
 * @author Andras
 */
public class GUITracker implements IGuiRecorderListener {
    protected GUIRecorder recorder;

    public GUITracker() {
        recorder = new GUIRecorder();
        recorder.addGuiRecorderListener(this);
    }

    public void hookListeners() {
        recorder.hookListeners();
    }

    public void unhookListeners() {
        recorder.unhookListeners();
    }

    public void elementsAdded(GUIRecorder recorder) {
        JavaSequence result = recorder.getResult();
        String javaCode = result.generateCode();
        result.clear();

        Activator.logMessage("Recorded event\n" + StringUtils.indentLines(javaCode, "   > "));
    }
}

