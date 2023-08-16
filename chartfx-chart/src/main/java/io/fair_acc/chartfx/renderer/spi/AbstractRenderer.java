package io.fair_acc.chartfx.renderer.spi;

import io.fair_acc.chartfx.Chart;
import io.fair_acc.chartfx.ui.css.CssPropertyFactory;
import io.fair_acc.chartfx.ui.css.StyleUtil;
import io.fair_acc.chartfx.utils.PropUtil;
import io.fair_acc.dataset.events.ChartBits;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.geometry.Orientation;

import io.fair_acc.chartfx.XYChart;
import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.chartfx.renderer.Renderer;
import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.DataSetError;
import io.fair_acc.dataset.spi.DoubleDataSet;
import io.fair_acc.dataset.spi.DoubleErrorDataSet;
import io.fair_acc.dataset.utils.NoDuplicatesList;
import io.fair_acc.dataset.utils.ProcessingProfiler;
import javafx.scene.Parent;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * @author rstein
 * @param <R> renderer generics
 */
public abstract class AbstractRenderer<R extends Renderer> extends Parent implements Renderer {

    protected final StyleableBooleanProperty showInLegend = css().createBooleanProperty(this, "showInLegend", true);
    private final ObservableList<DataSet> datasets = FXCollections.observableArrayList();
    private final ObservableList<Axis> axesList = FXCollections.observableList(new NoDuplicatesList<>());
    private final ObjectProperty<Chart> chart = new SimpleObjectProperty<>();

    public AbstractRenderer() {
        StyleUtil.addStyles(this, "renderer");
        PropUtil.runOnChange(() -> fireInvalidated(ChartBits.ChartLegend), showInLegend);
    }

    @Override
    public ObservableList<Axis> getAxes() {
        return axesList;
    }

    @Override
    public ObservableList<DataSet> getDatasets() {
        return datasets;
    }

    @Override
    public ObservableList<DataSet> getDatasetsCopy() {
        return getDatasetsCopy(getDatasets());
    }

    protected ObservableList<DataSet> getDatasetsCopy(final ObservableList<DataSet> localDataSets) {
        final long start = ProcessingProfiler.getTimeStamp();
        final ObservableList<DataSet> dataSets = FXCollections.observableArrayList();
        for (final DataSet dataSet : localDataSets) {
            if (dataSet instanceof DataSetError) {
                dataSets.add(new DoubleErrorDataSet(dataSet));
            } else {
                dataSets.add(new DoubleDataSet(dataSet));
            }
        }
        ProcessingProfiler.getTimeDiff(start);
        return dataSets;
    }

    public Axis getFirstAxis(final Orientation orientation) {
        for (final Axis axis : getAxes()) {
            if (axis.getSide() == null) {
                continue;
            }
            switch (orientation) {
            case VERTICAL:
                if (axis.getSide().isVertical()) {
                    return axis;
                }
                break;
            case HORIZONTAL:
            default:
                if (axis.getSide().isHorizontal()) {
                    return axis;
                }
                break;
            }
        }
        return null;
    }

    /**
     * Returns the first axis for a specific orientation and falls back to the first axis
     * of the chart if no such axis exists. The chart will automatically return a default
     * axis in case no axis is present.
     * Because this code adds axes automatically, it should not be called during chart setup
     * but only inside of rendering routines. Otherwise there is risk of duplicate axes if
     * things are called in the wrong order.
     *
     * @param orientation specifies if a horizontal or vertical axis is requested
     * @param fallback The chart from which to get the axis if no axis is present
     * @return The requested axis
     */
    protected Axis getFirstAxis(final Orientation orientation, final XYChart fallback) {
        final Axis axis = getFirstAxis(orientation);
        if (axis == null) {
            return fallback.getFirstAxis(orientation);
        }
        return axis;
    }

    /**
     * @return the instance of this AbstractDataSetManagement.
     */
    protected abstract R getThis();

    public Chart getChart() {
        return chart.get();
    }

    public ObjectProperty<Chart> chartProperty() {
        return chart;
    }

    public void setChart(Chart chart) {
        this.chart.set(chart);
    }

    /**
     * Sets whether DataSets attached to this renderer shall be shown in the legend
     *
     * @param state true (default) if data sets are supposed to be drawn
     * @return the renderer class
     */
    @Override
    public R setShowInLegend(final boolean state) {
        showInLegend.set(state);
        return getThis();
    }

    /**
     * Sets whether DataSets attached to this renderer shall be shown in the legend
     *
     * @return true (default) if data sets are supposed to be drawn
     */
    @Override
    public boolean showInLegend() {
        return showInLegend.get();
    }

    /**
     * Sets whether DataSets attached to this renderer shall be shown in the legend
     *
     * @return true (default) if data sets are supposed to be drawn
     */
    @Override
    public final BooleanProperty showInLegendProperty() {
        return showInLegend;
    }

    /**
     * @param prop property that causes the canvas to invalidate
     * @return property
     * @param <T> any type of property
     */
    protected <T extends Property<?>> T registerCanvasProp(T prop){
        PropUtil.runOnChange(this::invalidateCanvas, prop);
        return prop;
    }

    protected void invalidateCanvas() {
        fireInvalidated(ChartBits.ChartCanvas);
    }

    protected void fireInvalidated(IntSupplier bit) {
        var chart = getChart();
        if (chart != null) {
           chart.fireInvalidated(bit);
        }
    }

    protected CssPropertyFactory<AbstractRenderer<?>> css() {
        return CSS; // subclass specific CSS due to inheritance issues otherwise
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return css().getCssMetaData();
    }

    private static final CssPropertyFactory<AbstractRenderer<?>> CSS = new CssPropertyFactory<>(Parent.getClassCssMetaData());

}