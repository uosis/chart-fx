/*
 * Copyright (c) 2016 European Organisation for Nuclear Research (CERN), All Rights Reserved.
 */

package io.fair_acc.chartfx.plugins;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.text.Text;

import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.dataset.spi.utils.Tuple;

/**
 * Horizontal and vertical {@link Line} drawn on the plot area, crossing at the mouse cursor location, together with a
 * {@link Text} displaying the cursor coordinates in data units.
 * <p>
 * CSS style class names: {@value #STYLE_CLASS_PATH} and {@value #STYLE_CLASS_LABEL}
 *
 * @author Grzegorz Kruk
 */
public class CrosshairIndicator extends ChartPlugin {
    public static final String STYLE_CLASS_PATH = "chart-crosshair-path";
    public static final String STYLE_CLASS_LABEL = "chart-crosshair-label";
    private static final int LABEL_X_OFFSET = 15;
    private static final int LABEL_Y_OFFSET = 5;

    protected final Path crosshairPath = new Path();
    protected final Text coordinatesLabel = new Text();

    private final ObjectProperty<DataPointFormatter> formatter = new SimpleObjectProperty<>(this, "Default Datapoint formatter", new DataPointFormatter());

    /**
     * Creates a new instance of CrosshairIndicator class.
     */
    public CrosshairIndicator() {
        crosshairPath.getStyleClass().add(CrosshairIndicator.STYLE_CLASS_PATH);
        crosshairPath.setManaged(false);
        crosshairPath.setId("crosshairIndicator-Path");
        coordinatesLabel.getStyleClass().add(CrosshairIndicator.STYLE_CLASS_LABEL);
        coordinatesLabel.setManaged(false);
        coordinatesLabel.setId("crosshairIndicator-Label");

        getChartChildren().addAll(crosshairPath, coordinatesLabel);

        final EventHandler<MouseEvent> mouseMoveHandler = (final MouseEvent event) -> {
            if (!isMouseEventWithinCanvas(event)) {
                crosshairPath.setManaged(false);
                coordinatesLabel.setManaged(false);
                crosshairPath.setVisible(false);
                coordinatesLabel.setVisible(false);
                return;
            }

            final Bounds plotAreaBounds = getChart().getPlotArea().getBoundsInParent();
            updatePath(event, plotAreaBounds);
            updateLabel(event, plotAreaBounds);

            crosshairPath.setManaged(true);
            coordinatesLabel.setManaged(true);
            crosshairPath.setVisible(true);
            coordinatesLabel.setVisible(true);
        };
        registerInputEventHandler(MouseEvent.ANY, mouseMoveHandler);
    }

    private String formatLabelText(final Point2D displayPointInPlotArea) {
        final Axis yAxis = getChart().getFirstAxis(Orientation.VERTICAL);
        if (yAxis == null) {
            return getChart() + " - "
          + "no y-axis present to translate point " + displayPointInPlotArea;
        }
        Tuple<Number, Number> tuple = toDataPoint(yAxis, displayPointInPlotArea);
        if (tuple == null) {
            return "unknown coordinate";
        }
        return formatter.get().formatData(getChart(), tuple);
    }

    private void updateLabel(final MouseEvent event, final Bounds plotAreaBounds) {
        coordinatesLabel.setText(formatLabelText(getLocationInPlotArea(event)));

        final double width = coordinatesLabel.prefWidth(-1);
        final double height = coordinatesLabel.prefHeight(width);

        double xLocation = event.getX() + CrosshairIndicator.LABEL_X_OFFSET;
        double yLocation = event.getY() + CrosshairIndicator.LABEL_Y_OFFSET;

        if (xLocation + width > plotAreaBounds.getMaxX()) {
            xLocation = event.getX() - CrosshairIndicator.LABEL_X_OFFSET - width;
        }
        if (yLocation + height > plotAreaBounds.getMaxY()) {
            yLocation = event.getY() - CrosshairIndicator.LABEL_Y_OFFSET - height;
        }
        coordinatesLabel.resizeRelocate(xLocation, yLocation, width, height);
    }

    private void updatePath(final MouseEvent event, final Bounds plotAreaBounds) {
        final ObservableList<PathElement> path = crosshairPath.getElements();
        path.clear();
        path.add(new MoveTo(plotAreaBounds.getMinX() + 1, event.getY()));
        path.add(new LineTo(plotAreaBounds.getMaxX(), event.getY()));
        path.add(new MoveTo(event.getX(), plotAreaBounds.getMinY() + 1));
        path.add(new LineTo(event.getX(), plotAreaBounds.getMaxY()));
    }

    public DataPointFormatter getFormatter() {
        return formatter.get();
    }

    public ObjectProperty<DataPointFormatter> formatterProperty() {
        return formatter;
    }
}
