/*--------------------------------------------------------------*
  Copyright (C) 2006-2015 OpenSim Ltd.

  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package com.simulcraft.test.gui.access;

import java.util.List;

import junit.framework.Assert;

import org.eclipse.draw2d.FigureCanvas;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.draw2d.Viewport;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.draw2d.geometry.Point;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.omnetpp.common.util.IPredicate;
import org.omnetpp.common.util.ReflectionUtils;
import org.omnetpp.common.util.StringUtils;

import com.simulcraft.test.gui.core.UIStep;

public class FigureAccess
    extends ClickableAccess
{
    protected IFigure figure;

    public FigureAccess(IFigure figure) {
        this.figure = figure;
    }

    public IFigure getFigure() {
        return figure;
    }

    public IFigure getRootFigure() {
        IFigure currentFigure = figure;

        while (currentFigure.getParent() != null)
            currentFigure = currentFigure.getParent();

        return currentFigure;
    }

    public Canvas getCanvas() {
        IFigure rootFigure = getRootFigure();
        LightweightSystem lightweightSystem = (LightweightSystem)ReflectionUtils.getFieldValue(rootFigure, "this$0");
        return (Canvas)ReflectionUtils.getFieldValue(lightweightSystem, "canvas");
    }

    public FigureAccess getDescendantFigure(Class<? extends IFigure> clazz) {
        return (FigureAccess) createAccess(findDescendantFigure(figure, clazz));
    }

    public FigureAccess getDescendantFigure(IPredicate predicate) {
        return (FigureAccess) createAccess(findDescendantFigure(figure, predicate));
    }

    @SuppressWarnings("unchecked")
    public FigureAccess getChildFigure(Class<? extends IFigure> clazz, int index) {
        int k = 0;
        for (IFigure child : (List<IFigure>)figure.getChildren())
            if (child.getClass() == figure.getClass())
                    if (k == index)
                        return (FigureAccess) createAccess(child);
                    else
                        k++;
        Assert.assertTrue("no "+StringUtils.ordinal(index)+" "+clazz.toString()+" child in "+figure.toString(), false);
        return null;
    }

    @UIStep
    public void click(int button) {
        reveal();
        IFigure rootFigure = getRootFigure();
        Rectangle bounds = getAbsoluteBounds();
        Point[] testSpots = { bounds.getCenter(),
                              bounds.getTop().translate(0, 1), bounds.getBottom().translate(0, -1),
                              bounds.getLeft().translate(1, 0), bounds.getRight().translate(-1, 0),
                              bounds.getTopLeft().translate(1,1), bounds.getTopRight().translate(-1,0),
                              bounds.getBottomLeft().translate(1,-1), bounds.getBottomRight().translate(-1,-1)};
        for (Point p : testSpots)
            if (rootFigure.findFigureAt(p) == getFigure()) {
                click(button, p);
                return;
            }
    }

    @UIStep
    public void click(int button, org.eclipse.swt.graphics.Point point) {
        click(button, point.x, point.y);
    }

    @UIStep
    public void click(int button, Point point) {
        click(button, point.x, point.y);
    }

    @UIStep
    public void click(int button, int x, int y) {
        clickAbsolute(button, getCanvas().toDisplay(x, y));
    }

    public Rectangle getAbsoluteBounds() {
        Rectangle r = figure.getBounds().getCopy();
        figure.translateToAbsolute(r);
        return r;
    }

    protected org.eclipse.swt.graphics.Point toDisplay(Point point) {
        return toDisplay(point.x, point.y);
    }

    protected org.eclipse.swt.graphics.Point toDisplay(org.eclipse.swt.graphics.Point point) {
        return toDisplay(point.x, point.y);
    }

    public org.eclipse.swt.graphics.Point toDisplay(int x, int y) {
        Point point = new Point(x, y);
        figure.translateToAbsolute(point);
        return getCanvas().toDisplay(point.x, point.y);
    }


    @UIStep
    public void reveal() {
        // TODO rather call the viewer's reveal (see. FlyoutPaletteCompositeAccess.reveal()
        // copied from ScrollingGraphicalViewer.reveal(EditPart)
        IFigure target = getFigure();
        Viewport port = ((FigureCanvas)getCanvas()).getViewport();
        Rectangle exposeRegion = target.getBounds().getCopy();
        target = target.getParent();
        while (target != null && target != port) {
            target.translateToParent(exposeRegion);
            target = target.getParent();
        }
        exposeRegion.expand(5, 5);

        Dimension viewportSize = port.getClientArea().getSize();

        Point topLeft = exposeRegion.getTopLeft();
        Point bottomRight = exposeRegion.getBottomRight().translate(viewportSize.getNegated());
        Point finalLocation = new Point();
        if (viewportSize.width < exposeRegion.width)
            finalLocation.x = Math.min(bottomRight.x, Math.max(topLeft.x, port.getViewLocation().x));
        else
            finalLocation.x = Math.min(topLeft.x, Math.max(bottomRight.x, port.getViewLocation().x));

        if (viewportSize.height < exposeRegion.height)
            finalLocation.y = Math.min(bottomRight.y, Math.max(topLeft.y, port.getViewLocation().y));
        else
            finalLocation.y = Math.min(topLeft.y, Math.max(bottomRight.y, port.getViewLocation().y));

        ((FigureCanvas)getCanvas()).scrollSmoothTo(finalLocation.x, finalLocation.y);
    }

    public Rectangle getBounds() {
        return getFigure().getBounds();
    }
}
