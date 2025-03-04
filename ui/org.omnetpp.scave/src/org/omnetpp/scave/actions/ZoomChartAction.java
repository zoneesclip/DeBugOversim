/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.scave.actions;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.omnetpp.scave.ScavePlugin;
import org.omnetpp.scave.charting.ChartCanvas;
import org.omnetpp.scave.editors.ScaveEditor;

/**
 * Zooms in/out the chart of the active chart page in the active Scave editor.
 *
 * @author tomi
 */
public class ZoomChartAction extends AbstractScaveAction {
    public static final String IMAGE_ZOOMOUT = "icons/full/etool16/zoomout.png";
    public static final String IMAGE_ZOOMIN = "icons/full/etool16/zoomin.png";
    public static final String IMAGE_ZOOMTOFIT = "icons/full/etool16/zoomtofit.png";
    public static final String IMAGE_HZOOMIN = "icons/full/etool16/hzoomin.png";
    public static final String IMAGE_HZOOMOUT = "icons/full/etool16/hzoomout.png";
    public static final String IMAGE_VZOOMIN = "icons/full/etool16/vzoomin.png";
    public static final String IMAGE_VZOOMOUT = "icons/full/etool16/vzoomout.png";

    private double zoomFactor;
    private boolean horizontally;
    private boolean vertically;

    public ZoomChartAction(boolean horizontally, boolean vertically, double zoomFactor) {
        this.horizontally = horizontally;
        this.vertically = vertically;
        this.zoomFactor = zoomFactor;

        boolean both = horizontally && vertically;
        String inout = (zoomFactor == 0.0 ? "To Fit" :
                        zoomFactor > 1.0 ? "In" : "Out");
        //String dir = both ? "" : (horizontally ? " X" : " Y");
        String dir2 = both ? "" : (horizontally ? " Horizontally" : " Vertically");
        setText("Zoom " +  inout + dir2);
        setDescription("Zoom " + inout.toLowerCase() + " Chart " + dir2.toLowerCase());

        String imageId =
            zoomFactor == 0.0 ? IMAGE_ZOOMTOFIT :
            zoomFactor > 1.0 ? (both ? IMAGE_ZOOMIN : horizontally ? IMAGE_HZOOMIN : IMAGE_VZOOMIN) :
                (both ? IMAGE_ZOOMOUT : horizontally ? IMAGE_HZOOMOUT : IMAGE_VZOOMOUT);
        setImageDescriptor(ScavePlugin.getImageDescriptor(imageId));
    }

    @Override
    protected void doRun(ScaveEditor scaveEditor, IStructuredSelection selection) {
        ChartCanvas canvas = scaveEditor.getActiveChartCanvas();
        if (canvas != null) {
            if (horizontally) {
                if (zoomFactor == 0.0)
                    canvas.zoomToFitX();
                else
                    canvas.zoomXBy(zoomFactor);
            }
            if (vertically) {
                if (zoomFactor == 0.0)
                    canvas.zoomToFitY();
                else
                    canvas.zoomYBy(zoomFactor);
            }
        }
    }

    @Override
    protected boolean isApplicable(ScaveEditor editor, IStructuredSelection selection) {
        ChartCanvas canvas = editor.getActiveChartCanvas();
        if (canvas != null) {
            return zoomFactor > 1.0 && horizontally && canvas.getZoomX() < canvas.getMaxZoomX() ||
                   zoomFactor > 1.0 && vertically && canvas.getZoomX() < canvas.getMaxZoomX() ||
                   zoomFactor < 1.0 && horizontally && canvas.getMinZoomX() < canvas.getZoomX() ||
                   zoomFactor < 1.0 && vertically && canvas.getMinZoomY() < canvas.getZoomY();
        }
        return false;
    }
}
