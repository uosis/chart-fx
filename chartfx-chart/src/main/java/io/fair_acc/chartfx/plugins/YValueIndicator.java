/*
 * Copyright (c) 2017 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package io.fair_acc.chartfx.plugins;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;

import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.chartfx.ui.geometry.Side;
import io.fair_acc.dataset.event.EventSource;

/**
 * A horizontal line drawn on the plot area, indicating specified Y value, with an optional {@link #textProperty() text
 * label} describing the value.
 * <p>
 * Style Classes (from least to most specific):
 * <ul>
 * <li><b>Label:</b> {@code value-indicator-label, y-value-indicator-label, y-value-indicator-label[index]}</li>
 * <li><b>Line:</b> {@code value-indicator-line, y-value-indicator-line, y-value-indicator-line[index]}</li>
 * </ul>
 * where {@code [index]} corresponds to the index (zero based) of this indicator instance added to the
 * {@code XYChartPane}. For example class {@code y-value-indicator-label1} can be used to style label of the second
 * instance of this indicator added to the chart pane.
 *
 * @author mhrabia
 */
public class YValueIndicator extends AbstractSingleValueIndicator implements EventSource, ValueIndicator {
    /**
     * Creates a new instance indicating given Y value belonging to the specified {@code yAxis}.
     *
     * @param axis the axis this indicator is associated with
     * @param value a value to be marked
     */
    public YValueIndicator(final Axis axis, final double value) {
        this(axis, value, null);
    }

    /**
     * Creates a new instance indicating given Y value belonging to the specified {@code yAxis}, with the specified
     * {@link #textProperty() label}.
     *
     * @param axis the axis this indicator is associated with
     * @param value a value to be marked
     * @param text the text to be shown by the label. Value of {@link #textProperty()}.
     */
    public YValueIndicator(final Axis axis, final double value, final String text) {
        super(axis, value, text);
        setLabelHorizontalAnchor(HPos.RIGHT);
        setLabelPosition(0.975);

        pickLine.setOnMouseDragged(this::handleDragMouseEvent);
        triangle.setOnMouseDragged(this::handleDragMouseEvent);
        label.setOnMouseDragged(this::handleDragMouseEvent);
    }

    protected void handleDragMouseEvent(final MouseEvent mouseEvent) {
        if (!isEditable()) {
            return;
        }
        Point2D c = getChart().getPlotArea().sceneToLocal(mouseEvent.getSceneX(), mouseEvent.getSceneY());
        final double yPosData = getAxis().getValueForDisplay(c.getY() - dragDelta.y);

        if (getAxis().isValueOnAxis(yPosData)) {
            valueProperty().set(yPosData);
        }

        mouseEvent.consume();
    }

    @Override
    public void layoutChildren() {
        if (getChart() == null) {
            return;
        }
        final Bounds plotAreaBounds = getChart().getPlotForeground().sceneToLocal(getChart().getPlotArea().localToScene(getChart().getPlotArea().getBoundsInLocal()));
        final double minX = plotAreaBounds.getMinX();
        final double maxX = plotAreaBounds.getMaxX();
        final double minY = plotAreaBounds.getMinY();
        final double maxY = plotAreaBounds.getMaxY();

        final double yPos = minY + getAxis().getDisplayPosition(getValue());
        final double axisPos;
        if (getAxis().getSide().equals(Side.RIGHT)) {
            axisPos = getChart().getPlotForeground().sceneToLocal(getAxis().getCanvas().localToScene(0, 0)).getX() + 2;
            triangle.getPoints().setAll(0.0, 0.0, 8.0, 8.0, 8.0, -8.0);
        } else {
            axisPos = getChart().getPlotForeground().sceneToLocal(getAxis().getCanvas().localToScene(getAxis().getWidth(), 0)).getX() - 2;
            triangle.getPoints().setAll(0.0, 0.0, -8.0, 8.0, -8.0, -8.0);
        }

        if (yPos < minY || yPos > maxY) {
            getChartChildren().clear();
        } else {
            layoutLine(minX, yPos, maxX, yPos);
            layoutMarker(axisPos, yPos, minX, yPos);
            layoutLabel(new BoundingBox(minX, yPos, maxX - minX, 0), getLabelPosition(),
                    AbstractSingleValueIndicator.MIDDLE_POSITION);
        }
    }

    @Override
    public void updateStyleClass() {
        setStyleClasses(label, "y-", AbstractSingleValueIndicator.STYLE_CLASS_LABEL);
        setStyleClasses(line, "y-", AbstractSingleValueIndicator.STYLE_CLASS_LINE);
        setStyleClasses(triangle, "x-", AbstractSingleValueIndicator.STYLE_CLASS_MARKER);
    }
}
