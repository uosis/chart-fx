package io.fair_acc.chartfx.renderer.spi;

import static io.fair_acc.dataset.DataSet.DIM_Y;
import static io.fair_acc.dataset.DataSet.DIM_Z;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.fair_acc.chartfx.Chart;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.canvas.GraphicsContext;

import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.chartfx.renderer.ErrorStyle;
import io.fair_acc.chartfx.renderer.Renderer;
import io.fair_acc.dataset.AxisDescription;
import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.GridDataSet;
import io.fair_acc.dataset.event.EventListener;
import io.fair_acc.dataset.locks.DataSetLock;
import io.fair_acc.dataset.locks.DefaultDataSetLock;
import io.fair_acc.dataset.spi.DefaultAxisDescription;
import io.fair_acc.dataset.utils.AssertUtils;
import io.fair_acc.dataset.utils.ProcessingProfiler;

/**
 * @author rstein
 */
public class MountainRangeRenderer extends ErrorDataSetRenderer implements Renderer {
    private static final int MIN_DIM = 3;
    protected DoubleProperty mountainRangeOffset = new SimpleDoubleProperty(this, "mountainRangeOffset", 0.5);
    private final ObservableList<ErrorDataSetRenderer> renderers = FXCollections.observableArrayList();
    private final ObservableList<DataSet> empty = FXCollections.observableArrayList();
    private final WeakHashMap<Double, Integer> xWeakIndexMap = new WeakHashMap<>();
    private final WeakHashMap<Double, Integer> yWeakIndexMap = new WeakHashMap<>();
    private double mountainRangeExtra;

    public MountainRangeRenderer() {
        super();
        setDrawMarker(false);
        setDrawBars(false);
        setErrorType(ErrorStyle.NONE);
        xWeakIndexMap.clear();
        yWeakIndexMap.clear();
    }

    public MountainRangeRenderer(final double mountainRangeOffset) {
        this();
        setMountainRangeOffset(mountainRangeOffset);
    }

    /**
     * Returns the <code>mountainRangeOffset</code>.
     *
     * @return the <code>mountainRangeOffset</code>, i.e. vertical offset between subsequent data sets
     */
    public final double getMountainRangeOffset() {
        return mountainRangeOffset.get();
    }

    public final DoubleProperty mountainRangeOffsetProperty() {
        return mountainRangeOffset;
    }

    @Override
    public List<DataSet> render(final GraphicsContext gc, final Chart chart, final int dataSetOffset,
                                final ObservableList<DataSet> datasets) {
        if (chart == null) {
            throw new InvalidParameterException("must be derivative of XYChart for renderer - " + this.getClass().getSimpleName());
        }
        final long start = ProcessingProfiler.getTimeStamp(); // NOPMD - time keeping needs to be defined here

        final Axis yAxis = chart.getYAxis();

        // make local copy and add renderer specific data sets
        final List<DataSet> localDataSetList = new ArrayList<>(datasets);
        localDataSetList.addAll(getDatasets());

        final double zRangeMin = localDataSetList.stream().mapToDouble(ds -> ds.getAxisDescription(DIM_Z).getMin()).min().orElse(-1.0);
        final double zRangeMax = localDataSetList.stream().mapToDouble(ds -> ds.getAxisDescription(DIM_Z).getMax()).max().orElse(+1.0);

        // render in reverse order
        for (int dataSetIndex = localDataSetList.size() - 1; dataSetIndex >= 0; dataSetIndex--) {
            final DataSet dataSet = localDataSetList.get(dataSetIndex);

            // detect and fish-out 3D DataSet, ignore others
            if (!dataSet.isVisible() || !(dataSet instanceof GridDataSet)) {
                continue;
            }

            dataSet.lock().readLockGuardOptimistic(() -> {
                xWeakIndexMap.clear();
                yWeakIndexMap.clear();
                mountainRangeExtra = getMountainRangeOffset();

                final double max = zRangeMax * (1.0 + mountainRangeExtra);
                final boolean autoRange = yAxis.isAutoRanging();
                if (autoRange && (zRangeMin != yAxis.getMin() || max != yAxis.getMax())) {
                    yAxis.setAutoRanging(false);
                    yAxis.setMin(zRangeMin);
                    yAxis.setMax(max);
                    yAxis.setTickUnit(Math.abs(max - zRangeMin) / 10.0);
                    yAxis.forceRedraw();
                }
                yAxis.setAutoRanging(autoRange);

                final int yCountMax = ((GridDataSet) dataSet).getShape(DIM_Y);
                checkAndRecreateRenderer(yCountMax);

                for (int index = yCountMax - 1; index >= 0; index--) {
                    renderers.get(index).getDatasets().setAll(new Demux3dTo2dDataSet((GridDataSet) dataSet, index, zRangeMin, max)); // NOPMD -- new necessary here
                    renderers.get(index).render(gc, chart, 0, empty);
                }
            });
        }

        ProcessingProfiler.getTimeDiff(start);
        return localDataSetList;
    }

    /**
     * Sets the <code>dashSize</code> to the specified value. The dash is the horizontal line painted at the ends of the
     * vertical line. It is not painted if set to 0.
     *
     * @param mountainRangeOffset t<code>mountainRangeOffset</code>, i.e. vertical offset between subsequent data sets
     * @return itself (fluent design)
     */
    public MountainRangeRenderer setMountainRangeOffset(final double mountainRangeOffset) { // NOPMD -- fluent design setter returns class
        AssertUtils.gtEqThanZero("mountainRangeOffset", mountainRangeOffset);
        this.mountainRangeOffset.setValue(mountainRangeOffset);
        return this;
    }

    private void checkAndRecreateRenderer(final int nRenderer) {
        if (renderers.size() == nRenderer) {
            // all OK
            return;
        }

        if (nRenderer > renderers.size()) {
            for (int i = renderers.size(); i < nRenderer; i++) {
                final ErrorDataSetRenderer newRenderer = new ErrorDataSetRenderer(); // NOPMD -- 'new' needed in this context
                newRenderer.bind(this);
                // do not show history sets in legend (single exception to
                // binding)
                newRenderer.showInLegendProperty().unbind();
                newRenderer.setShowInLegend(false);
                renderers.add(newRenderer);
            }
            return;
        }

        // require less renderer -> remove first until we have the right number
        // needed
        while (nRenderer < renderers.size()) {
            renderers.remove(0);
        }
    }

    private class Demux3dTo2dDataSet implements DataSet {
        private static final long serialVersionUID = 3914728138839091421L;
        private final transient DataSetLock<DataSet> localLock = new DefaultDataSetLock<>(this);
        private final transient AtomicBoolean autoNotify = new AtomicBoolean(true);
        private final GridDataSet dataSet;
        private final int yIndex;
        private final double zMin;
        private final double zMax;
        private final double yShift;
        private final transient List<EventListener> updateListener = new ArrayList<>();
        private final transient List<AxisDescription> axesDescriptions = new ArrayList<>(Arrays.asList( //
                new DefaultAxisDescription(DIM_X, "x-Axis", "a.u."), //
                new DefaultAxisDescription(DIM_Y, "y-Axis", "a.u.")));

        public Demux3dTo2dDataSet(final GridDataSet sourceDataSet, final int selectedYIndex, final double zMin, final double zMax) {
            super();
            dataSet = sourceDataSet;
            yIndex = selectedYIndex;
            this.zMin = zMin;
            this.zMax = zMax;
            yShift = dataSet.getShape(DIM_Y) > 0 ? mountainRangeExtra * dataSet.getAxisDescription(DIM_Z).getMax() * yIndex / dataSet.getShape(DIM_Y) : 0;
        }

        @Override
        public AtomicBoolean autoNotification() {
            return autoNotify;
        }

        @Override
        public double get(final int dimIndex, final int i) {
            switch (dimIndex) {
            case DIM_X:
                return dataSet.getGrid(dimIndex, i);
            case DIM_Y:
                return dataSet.get(DIM_Z, i, yIndex) + yShift;
            default:
                throw new IllegalArgumentException("dinIndex " + dimIndex + " not defined");
            }
        }

        @Override
        public List<AxisDescription> getAxisDescriptions() {
            return axesDescriptions;
        }

        @Override
        public int getDataCount() {
            return dataSet.getShape(DIM_X);
        }

        @Override
        public String getDataLabel(final int index) {
            return dataSet.getDataLabel(index);
        }

        @Override
        public int getDimension() {
            return 2;
        }

        @Override
        public int getIndex(int dimIndex, double... value) {
            AssertUtils.checkArrayDimension("value", value, 1);
            switch (dimIndex) {
            case DIM_X:
                // added computation of hash since this is recomputed quite often (and the same) for each slice
                return xWeakIndexMap.computeIfAbsent(value[0], key -> dataSet.getGridIndex(DIM_X, key));
            case DIM_Y:
                // added computation of hash since this is recomputed quite often (and the same) for each slice
                return yWeakIndexMap.computeIfAbsent(value[0], key -> dataSet.getGridIndex(DIM_Y, key));
            default:
                throw new IndexOutOfBoundsException("dimIndex=" + dimIndex + " out of range");
            }
        }

        @Override
        public String getName() {
            return dataSet.getName() + ":slice#" + yIndex;
        }

        @Override
        public String getStyle() {
            return dataSet.getStyle();
        }

        @Override
        public String getStyle(final int index) {
            return null;
        }

        @Override
        public double[] getValues(final int dimIndex) {
            switch (dimIndex) {
            case DIM_X:
                return dataSet.getGridValues(dimIndex);
            case DIM_Y:
                final double[] result = new double[dataSet.getShape(DIM_X)];
                for (int i = 0; i < result.length; i++) {
                    result[i] = dataSet.getValue(DIM_Z, i, yIndex) + yShift;
                }
                return result;
            default:
                throw new IllegalArgumentException("dinIndex " + dimIndex + " not defined");
            }
        }

        @Override
        public boolean isAutoNotification() {
            return autoNotify.get();
        }

        @Override
        public DataSetLock<DataSet> lock() {
            // empty implementation since the superordinate DataSet3D lock is being held/protecting this data set
            return localLock;
        }

        @Override
        public DataSet recomputeLimits(int dimension) {
            this.getAxisDescription(DIM_X).set(dataSet.getAxisDescription(DIM_X));
            this.getAxisDescription(DIM_Y).set(zMin, zMax);
            return this;
        }

        @Override
        public DataSet setStyle(final String style) {
            return dataSet.setStyle(style);
        }

        @Override
        public double getValue(final int dimIndex, final double... x) {
            return 0;
        }

        @Override
        public DataSet set(final DataSet other, final boolean copy) {
            throw new UnsupportedOperationException("copy setter not implemented for Demux3dTo2dDataSet");
        }

        @Override
        public boolean isVisible() {
            return dataSet.isVisible();
        }

        @Override
        public DataSet setVisible(boolean visible) {
            return dataSet.setVisible(visible);
        }

        @Override
        public List<EventListener> updateEventListener() {
            return updateListener;
        }
    }
}
