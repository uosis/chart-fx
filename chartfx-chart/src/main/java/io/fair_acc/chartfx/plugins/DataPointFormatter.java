package io.fair_acc.chartfx.plugins;

import io.fair_acc.chartfx.Chart;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.util.StringConverter;

import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.chartfx.axes.spi.MetricPrefix;
import io.fair_acc.chartfx.renderer.Renderer;
import io.fair_acc.chartfx.ui.geometry.Side;
import io.fair_acc.dataset.spi.utils.Tuple;

/**
 * An abstract plugin with associated formatters for X and Y value of the data. For details see {@link #formatData}.
 *
 * @author Grzegorz Kruk
 * @author rstein
 */
public class DataPointFormatter {
    private final ObjectProperty<StringConverter<Number>> xValueFormatter = new SimpleObjectProperty<>(this, "xValueFormatter");
    private final ObjectProperty<StringConverter<Number>> yValueFormatter = new SimpleObjectProperty<>(this, "yValueFormatter");
    private StringConverter<Number> defaultXValueFormatter;
    private StringConverter<Number> defaultYValueFormatter;

    /**
     * Creates a new instance of AbstractDataIndicator.
     */
    public DataPointFormatter() {
        super();
        defaultXValueFormatter = DataPointFormatter.createDefaultFormatter(null);
        defaultYValueFormatter = DataPointFormatter.createDefaultFormatter(null);
    }

    /**
     * Returns the value of the {@link #xValueFormatterProperty()}.
     *
     * @return the X value formatter
     */
    public final StringConverter<Number> getXValueFormatter() {
        return xValueFormatterProperty().get();
    }

    /**
     * Returns the value of the {@link #xValueFormatterProperty()}.
     *
     * @return the X value formatter
     */
    public final StringConverter<Number> getYValueFormatter() {
        return yValueFormatterProperty().get();
    }

    /**
     * Sets the value of the {@link #xValueFormatterProperty()}.
     *
     * @param formatter the X value formatter
     */
    public final void setXValueFormatter(final StringConverter<Number> formatter) {
        xValueFormatterProperty().set(formatter);
    }

    /**
     * Sets the value of the {@link #xValueFormatterProperty()}.
     *
     * @param formatter the X value formatter
     */
    public final void setYValueFormatter(final StringConverter<Number> formatter) {
        yValueFormatterProperty().set(formatter);
    }

    /**
     * StringConverter used to format X values. If {@code null} a default will be used.
     *
     * @return the X value formatter property
     */
    public final ObjectProperty<StringConverter<Number>> xValueFormatterProperty() {
        return xValueFormatter;
    }

    /**
     * StringConverter used to format Y values. If {@code null} a default will be used.
     *
     * @return the Y value formatter property
     */
    public final ObjectProperty<StringConverter<Number>> yValueFormatterProperty() {
        return yValueFormatter;
    }

    private StringConverter<Number> getValueFormatter(final Axis axis, final StringConverter<Number> formatter, final StringConverter<Number> defaultFormatter) {
        StringConverter<Number> valueFormatter = formatter;
        if (valueFormatter == null) {
            valueFormatter = axis.getTickLabelFormatter();
        }
        if (valueFormatter == null) {
            valueFormatter = defaultFormatter;
        }
        return valueFormatter;
    }

    private StringConverter<Number> getXValueFormatter(final Axis xAxis) {
        return getValueFormatter(xAxis, getXValueFormatter(), defaultXValueFormatter);
    }

    private StringConverter<Number> getYValueFormatter(final Axis yAxis) {
        return getValueFormatter(yAxis, getYValueFormatter(), defaultYValueFormatter);
    }

    /**
     * Formats the data to be displayed by this plugin. Uses the specified {@link #xValueFormatterProperty()} and {@link #yValueFormatterProperty()} to obtain the corresponding formatters.
     * <p>
     * Can be overridden to modify formatting of the data.
     *
     * @param chart reference to chart
     *
     * @param data  the data point to be formatted
     * @return formatted data
     */
    protected String formatData(final Chart chart, final Tuple<Number, Number> data) {
        return formatData(chart.getAxes(), data);
    }

    /**
     * Formats the data to be displayed by this plugin. Uses the specified {@link #xValueFormatterProperty()} and {@link #yValueFormatterProperty()} to obtain the corresponding formatters.
     * <p>
     * Can be overridden to modify formatting of the data.
     *
     * @param renderer The renderer specifying the axes to format the data for
     * @param data  the data point to be formatted
     * @return formatted data
     */
    protected String formatData(final Renderer renderer, final Tuple<Number, Number> data) {
        return formatData(renderer.getAxes(), data);
    }
    protected String formatData(final ObservableList<Axis> axes, final Tuple<Number, Number> data) {
        if (axes.size() == 2) { // special case of only two axes
            // Get Axes for the Renderer
            final Axis xAxis = Chart.getFirstAxis(axes, Orientation.HORIZONTAL);
            final Axis yAxis = Chart.getFirstAxis(axes, Orientation.VERTICAL);
            if (xAxis == null || yAxis == null) {
                return String.format("DataPoint@(%.3f,%.3f)", data.getXValue().doubleValue(), data.getYValue().doubleValue());
            }
            return getXValueFormatter(xAxis).toString(data.getXValue()) + ", " + getYValueFormatter(yAxis).toString(data.getYValue());
        }

        // any other axes
        final StringBuilder result = new StringBuilder();
        for (final Axis axis : axes) {
            final Side side = axis.getSide();
            if (side == null) {
                continue;
            }

            final String axisPrimaryLabel = axis.getName();
            String axisUnit = axis.getUnit();
            final String axisPrefix = MetricPrefix.getShortPrefix(axis.getUnitScaling());
            final boolean isAutoScaling = axis.isAutoUnitScaling();
            if (isAutoScaling && axisUnit == null) {
                axisUnit = " a.u.";
            }

            result.append(axisPrimaryLabel).append(" = ");
            result.append(side.isHorizontal() ? getXValueFormatter(axis).toString(data.getXValue()) : getYValueFormatter(axis).toString(data.getYValue()));
            if (axisUnit != null) {
                result.append(axisPrimaryLabel).append(" [").append(axisPrefix).append(axisUnit).append(']');
            }
            result.append('\n');
        }

        return result.toString();
    }

    private static StringConverter<Number> createDefaultFormatter(final Axis axis) {
        // if (axis instanceof Axis) {
        // final io.fair_acc.chartfx.axes.spi.format.DefaultFormatter numberConverter
        // = new
        // io.fair_acc.chartfx.axes.spi.format.DefaultFormatter(
        // axis);
        // return numberConverter;
        // }
        // if (axis instanceof NumberAxis) { //TODO: re-enable
        // return (StringConverter<Number>) new
        // NumberAxis.DefaultFormatter((NumberAxis) axis);
        // }
        return new DataPointFormatter.DefaultFormatter<>();
    }

    private static class DefaultFormatter<T> extends StringConverter<T> {
        @Override
        public final T fromString(final String string) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString(final T value) {
            return String.valueOf(value);
        }
    }
}
