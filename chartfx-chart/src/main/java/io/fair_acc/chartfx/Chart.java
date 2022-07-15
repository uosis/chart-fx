package io.fair_acc.chartfx;

import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.*;
import javafx.geometry.*;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.stage.Window;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.chartfx.axes.spi.AbstractAxis;
import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.legend.Legend;
import io.fair_acc.chartfx.legend.spi.DefaultLegend;
import io.fair_acc.chartfx.plugins.ChartPlugin;
import io.fair_acc.chartfx.renderer.PolarTickStep;
import io.fair_acc.chartfx.renderer.Renderer;
import io.fair_acc.chartfx.renderer.spi.ErrorDataSetRenderer;
import io.fair_acc.chartfx.renderer.spi.GridRenderer;
import io.fair_acc.chartfx.renderer.spi.LabelledMarkerRenderer;
import io.fair_acc.chartfx.ui.ChartLayoutAnimator;
import io.fair_acc.chartfx.ui.HidingPane;
import io.fair_acc.chartfx.ui.css.CssPropertyFactory;
import io.fair_acc.chartfx.ui.geometry.Side;
import io.fair_acc.chartfx.utils.FXUtils;
import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.event.EventListener;
import io.fair_acc.dataset.utils.AssertUtils;
import io.fair_acc.dataset.utils.ProcessingProfiler;

/**
 * Reimplementation of the Chart class with new layout and rendering paradigm
 */
public class Chart extends Region implements Observable{
    private static final Logger LOGGER = LoggerFactory.getLogger(Chart.class);
    protected static final boolean DEBUG = false; // for more verbose debugging
    protected static final int BURST_LIMIT_MS = 15;

    public Chart() {
        getRenderers().addListener(this::rendererChanged);
        getDatasets().addListener(datasetChangeListener);
        getAxes().addListener(axesChangeListener);
        // update listener to propagate axes changes to chart changes
        getAxes().addListener(axesChangeListenerLocal);

        // old chart
        for (int dim = 0; dim < axes.size(); dim++) {
            final Axis axis = axes.get(dim);
            if (!(axis instanceof AbstractAxis)) {
                continue;
            }
            final AbstractAxis abstractAxis = (AbstractAxis) axis;
            if (abstractAxis.getDimIndex() < 0) {
                abstractAxis.setDimIndex(dim);
            }
        }

        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setPadding(Insets.EMPTY);

        plotBackground.getStyleClass().setAll("chart-plot-background");

        if (!canvas.isCache()) {
            canvas.setCache(true);
            canvas.setCacheHint(CacheHint.QUALITY);
        }

        // add plugin handling and listeners
        getPlugins().addListener(pluginsChangedListener);

        titleLabel.setAlignment(Pos.CENTER);

        // register listener in tool bar FlowPane
        // toolBar.registerListener();

        // getTitleLegendPane(Side.TOP).getChildren().add(titleLabel);

        legendVisibleProperty().addListener((ch, old, visible) -> {
            if (getLegend() == null) {
                return;
            }
            getLegend().getNode().setVisible(visible);
        });

        // set CSS stuff
        titleLabel.getStyleClass().add("chart-title");
        getStyleClass().add("chart");
        // axesAndCanvasPane.getStyleClass().add("chart-content");

        registerShowingListener(); // NOPMD - unlikely but allowed override

        gridRenderer.horizontalGridLinesVisibleProperty().addListener(gridLineVisibilitychange);
        gridRenderer.verticalGridLinesVisibleProperty().addListener(gridLineVisibilitychange);
        gridRenderer.getHorizontalMinorGrid().visibleProperty().addListener(gridLineVisibilitychange);
        gridRenderer.getVerticalMinorGrid().visibleProperty().addListener(gridLineVisibilitychange);
        gridRenderer.drawOnTopProperty().addListener(gridLineVisibilitychange);

        getRenderers().addListener(this::rendererChanged);
        getRenderers().add(new ErrorDataSetRenderer());

        axes.addListener((ListChangeListener<Axis>) c -> {
            while (c.next()) {
                getChildren().removeAll(c.getRemoved());
                c.getAddedSubList().forEach(ax -> getChildren().add((Node) ax));
            }
        });
        getChildren().addAll(plotBackground, canvas, canvasForeground, plotForeGround, toolBarPane, legend.get().getNode());
    }

    // ****************
    // Stylesheet Handling
    // ****************
    private static final String CHART_CSS = Objects.requireNonNull(Chart.class.getResource("chart.css")).toExternalForm();
    private static final CssPropertyFactory<Chart> CSS = new CssPropertyFactory<>(Region.getClassCssMetaData());

    /**
     * @return The CssMetaData associated with this class, which may include the CssMetaData of its super classes.
     * @since JavaFX 8.0
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS.getCssMetaData();
    }

    /**
     * @return unmodifiable list of the controls css styleable properties
     * @since JavaFX 8.0
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return Chart.getClassCssMetaData();
    }

    @Override
    public String getUserAgentStylesheet() {
        return CHART_CSS;
    }

    // *****************
    // Event Handling
    // *****************
    protected final List<InvalidationListener> listeners = new ArrayList<>();
    protected final BooleanProperty autoNotification = new SimpleBooleanProperty(this, "autoNotification", true);

    protected void executeFireInvalidated() {
        new ArrayList<>(listeners).forEach(listener -> listener.invalidated(this));
    }

    // @Override
    public void addListener(final InvalidationListener listener) {
        Objects.requireNonNull(listener, "InvalidationListener must not be null");
        listeners.add(listener);
    }

    /**
     * Notifies listeners that the data has been invalidated. If the data is added to the chart, it triggers repaint.
     *
     * @return itself (fluent design)
     */
    public Chart fireInvalidated() {
        synchronized (autoNotification) {
            if (!isAutoNotification() || listeners.isEmpty()) {
                return this;
            }
        }

        if (Platform.isFxApplicationThread()) {
            executeFireInvalidated();
        } else {
            Platform.runLater(this::executeFireInvalidated);
        }

        return this;
    }

    public BooleanProperty autoNotificationProperty() {
        return autoNotification;
    }

    public boolean isAutoNotification() {
        return autoNotification.get();
    }

    // @Override
    public void removeListener(final InvalidationListener listener) {
        listeners.remove(listener);
    }

    public void setAutoNotification(final boolean flag) {
        autoNotification.set(flag);
    }

    // *****************
    // Animation
    // *****************
    /**
     * When true any data changes will be animated.
     */
    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", false);
    /**
     * Animator for animating stuff on the chart
     */
    protected final ChartLayoutAnimator animator = new ChartLayoutAnimator(this);

    /**
     * This is used to check if any given animation should run. It returns true if animation is enabled and the node is
     * visible and in a scene.
     *
     * @return true if chart should animate
     */
    protected final boolean shouldAnimate() {
        return isAnimated() && getScene() != null;
    }

    /**
     * Play the given animation on every frame of the animation the chart will be relayed out until the animation
     * finishes. So to add a animation to a chart, create a animation on data model, during layoutChartContent() map
     * data model to nodes then call this method with the animation.
     *
     * @param animation The animation to play
     */
    protected void animate(final Animation animation) {
        animator.animate(animation);
    }

    /**
     * Play an animation involving the given keyframes. On every frame of the animation the chart will be relayed out
     *
     * @param keyFrames Array of KeyFrames to play
     */
    public void animate(final KeyFrame... keyFrames) {
        animator.animate(keyFrames);
    }

    public final BooleanProperty animatedProperty() {
        return animated;
    }

    public final boolean isAnimated() {
        return animated.get();
    }

    public final void setAnimated(final boolean value) {
        animated.set(value);
    }

    // ****************
    // chart component access and configuration
    // ****************
    protected final Pane plotBackground = new Pane();
    protected final Pane plotForeGround = new Pane();
    protected final Pane canvasForeground = new Pane();

    /**
     * @return the actual canvas the data is being drawn upon
     */
    public final Canvas getCanvas() {
        return canvas;
    }

    public final Pane getCanvasForeground() {
        return canvasForeground;
    }
    public final Node getPlotArea() {
        return canvas;
    }

    public final Pane getPlotBackground() {
        return plotBackground;
    }

    public final Pane getPlotForeground() {
        return plotForeGround;
    }

    // ******************
    // Measurement Bar
    // ******************
    protected final Map<Side, Pane> measurementBar = new HashMap<>(4);
    private final StyleableObjectProperty<Side> measurementBarSide = CSS.createObjectProperty(this, "measurementBarSide", Side.RIGHT, false,
            StyleConverter.getEnumConverter(Side.class), (oldVal, newVal) -> {
                AssertUtils.notNull("Side must not be null", newVal);
                return newVal;
            }, this::requestLayout);

    public final Pane getMeasurementBar(final Side side) {
        return Objects.requireNonNullElseGet(measurementBar.get(side), Pane::new);
    }

    public final Side getMeasurementBarSide() {
        return measurementBarSide.getValue();
    }

    public final void setMeasurementBarSide(final Side value) {
        measurementBarSide.set(value);
    }

    public final ObjectProperty<Side> measurementBarSideProperty() {
        return measurementBarSide;
    }

    // ****************
    // Title
    // ****************
    protected final Label titleLabel = new Label();

    protected final StringProperty title = new SimpleStringProperty(this, "title", "") {
        @Override
        protected void invalidated() {
            titleLabel.setText(get());
        }
    };

    private final StyleableObjectProperty<Side> titleSide = CSS.createObjectProperty(this, "titleSide", Side.TOP, false,
            StyleConverter.getEnumConverter(Side.class), (oldVal, newVal) -> AssertUtils.notNull("Side must not be null", newVal) , this::requestLayout);
    public final String getTitle() {
        return title.get();
    }

    public final Side getTitleSide() {
        return titleSide.get();
    }

    public final void setTitle(final String value) {
        title.set(value);
    }

    public final void setTitleSide(final Side value) {
        titleSide.set(value);
    }

    public final void setTitlePaint(final Paint paint) {
        titleLabel.setTextFill(paint);
    }

    public final StringProperty titleProperty() {
        return title;
    }

    public final ObjectProperty<Side> titleSideProperty() {
        return titleSide;
    }

    // **************
    // Toolbar
    // **************
    protected final ToolBar toolBar = new ToolBar();
    protected final HidingPane toolBarPane = new HidingPane(toolBar);
    private final StyleableObjectProperty<Side> toolBarSide = CSS.createObjectProperty(this, "toolBarSide", Side.TOP, false,
            StyleConverter.getEnumConverter(Side.class), (oldVal, newVal) -> AssertUtils.notNull("Side must not be null", newVal) , this::requestLayout);

    public final ToolBar getToolBar() {
        return toolBar;
    }

    public final ObjectProperty<Side> getToolBarSideProperty() {
        return toolBarSide;
    }

    public final Side getToolBarSide() {
        return toolBarSideProperty().get();
    }
    public Chart setToolBarPinned(boolean value) {
        toolBarPinnedProperty().set(value);
        return this;
    }

    public final void setToolBarSide(final Side value) {
        toolBarSide.set(value);
    }

    public BooleanProperty toolBarPinnedProperty() {
        return toolBarPane.sticky;
    }

    public final ObjectProperty<Side> toolBarSideProperty() {
        return toolBarSide;
    }

    public boolean isToolBarPinned() {
        return toolBarPinnedProperty().get();
    }

    // *****************
    // Showing plot
    // this is used to disable all charting computations if the plot is not visible in the UI
    // *****************
    protected final BooleanProperty showing = new SimpleBooleanProperty(this, "showing", false);
    protected final ChangeListener<? super Boolean> showingListener = (ch2, o, n) -> showing.set(n);
    protected final ChangeListener<? super Window> windowPropertyListener = (ch1, oldWindow, newWindow) -> {
        if (oldWindow != null) {
            oldWindow.showingProperty().removeListener(showingListener);
        }
        if (newWindow == null) {
            showing.set(false);
            return;
        }
        newWindow.showingProperty().addListener(showingListener);
    };
    private final ChangeListener<? super Scene> scenePropertyListener = (ch, oldScene, newScene) -> {
        if (oldScene == newScene) {
            return;
        }
        if (oldScene != null) {
            // remove listener
            oldScene.windowProperty().removeListener(windowPropertyListener);
        }

        if (newScene == null) {
            showing.set(false);
            return;
        }

        // add listener
        newScene.windowProperty().addListener(windowPropertyListener);
    };

    /**
     * @return true: if chart is being visible in Scene/Window
     */
    public boolean isShowing() {
        return showing.get();
    }

    /**
     * @return property indicating if chart is actively visible in Scene/Window
     */
    public ReadOnlyBooleanProperty showingProperty() {
        return showing;
    }

    protected void registerShowingListener() {
        sceneProperty().addListener(scenePropertyListener);

        showing.addListener((ch, o, n) -> {
            if (Boolean.TRUE.equals(n)) {
                // requestLayout();

                // alt implementation in case of start-up issues
                final KeyFrame kf1 = new KeyFrame(Duration.millis(20), e -> requestLayout());

                final Timeline timeline = new Timeline(kf1);
                Platform.runLater(timeline::play);
            }
        });
    }

    // *****************
    // Legend
    // *****************
    /** When true the chart will display a legend if the chart implementation supports a legend. */
    private final StyleableBooleanProperty legendVisible = CSS.createBooleanProperty(this, "legendVisible", true, () -> {
        updateLegend(getDatasets(), getRenderers());
        requestLayout();
    });
    /**
     * The side of the chart where the legend should be displayed default value Side.BOTTOM
     */
    private final StyleableObjectProperty<Side> legendSide = CSS.createObjectProperty(this, "legendSide", Side.BOTTOM, false,
            StyleConverter.getEnumConverter(Side.class), (oldVal, newVal) -> {
                AssertUtils.notNull("Side must not be null", newVal);
                final Legend legend = getLegend();
                legend.setVertical(newVal.isVertical());
                return newVal;
            }, this::requestLayout);
    /**
     * The node to display as the Legend. Subclasses can set a node here to be displayed on a side as the legend. If no
     * legend is wanted then this can be set to null
     */
    private final ObjectProperty<Legend> legend = new SimpleObjectProperty<>(this, "legend", new DefaultLegend());
    public final boolean isLegendVisible() {
        return legendVisible.getValue();
    }

    public final Legend getLegend() {
        return legend.getValue();
    }

    public final Side getLegendSide() {
        return legendSide.getValue();
    }

    public final void setLegend(final Legend value) {
        legend.set(value);
    }

    public final void setLegendSide(final Side value) {
        legendSide.set(value);
    }

    public final void setLegendVisible(final boolean value) {
        legendVisible.set(value);
    }

    public final ObjectProperty<Side> legendSideProperty() {
        return legendSide;
    }

    public final BooleanProperty legendVisibleProperty() {
        return legendVisible;
    }

    public final ObjectProperty<Legend> legendProperty() {
        return legend;
    }

    protected void updateLegend(final List<DataSet> dataSets, final List<Renderer> renderers) {
        final Legend legend = getLegend();
        if (legend == null) {
            return;
        }
        legend.updateLegend(dataSets, renderers);
    }

    // *****************
    // Plugins
    // *****************
    public final ObservableList<ChartPlugin> plugins = FXCollections.observableArrayList();
    private final Map<ChartPlugin, Group> pluginGroups = new HashMap<>();
    protected final ListChangeListener<ChartPlugin> pluginsChangedListener = this::pluginsChanged;

    public final ObservableList<ChartPlugin> getPlugins() {
        return plugins;
    }

    protected void layoutPluginsChildren() {
        plugins.forEach(ChartPlugin::layoutChildren);
    }

    protected void pluginsChanged(final ListChangeListener.Change<? extends ChartPlugin> change) {
        // update chart property
        while (change.next()) {
            change.getRemoved().stream().filter(c -> c.getChart() == this).forEach(c -> {
                c.setChart(null);
                getPlotForeground().getChildren().remove(pluginGroups.remove(c));
            });
            change.getAddedSubList().forEach(c -> {
                c.setChart(this);
                final var group = new Group(c.getChartChildren());
                pluginGroups.put(c, group);
                getPlotForeground().getChildren().add(group);
            });
        }
        requestLayout();
    }

    // *****************
    // Renderers
    // *****************
    public final ObservableList<Renderer> renderers = FXCollections.observableArrayList();

    /**
     * @return observable list of associated chart renderers
     */
    public ObservableList<Renderer> getRenderers() {
        return renderers;
    }

    protected void rendererChanged(final ListChangeListener.Change<? extends Renderer> change) {
        FXUtils.assertJavaFxThread();
        while (change.next()) {
            // handle added renderer
            change.getAddedSubList().forEach(renderer -> {
                // update legend and recalculateLayout on datasetChange
                renderer.getDatasets().addListener(datasetChangeListener);
                // add listeners to all datasets already in the renderer
                renderer.getDatasets().forEach(set -> set.addListener(dataSetDataListener));
            });

            // handle removed renderer
            change.getRemoved().forEach(renderer -> {
                renderer.getDatasets().removeListener(datasetChangeListener);
                renderer.getDatasets().forEach(set -> set.removeListener(dataSetDataListener));
            });
        }
        // reset change to allow derived classes to add additional listeners to renderer changes
        change.reset();

        requestLayout();
        updateLegend(getDatasets(), getRenderers());
    }

    // *****************
    // Coordinate Transformation
    // TODO: should this be handled in a coordinate class?
    // *****************
    /**
     * Translates point from chart pane coordinates to the plot area coordinates.
     *
     * @param xCoord the x coordinate within XYChartPane coordinates system
     * @param yCoord the y coordinate within XYChartPane coordinates system
     * @return point in plot area coordinates
     */
    public final Point2D toPlotArea(final double xCoord, final double yCoord) {
        final Bounds plotAreaBounds = getCanvas().getBoundsInParent();
        return new Point2D(xCoord - plotAreaBounds.getMinX(), yCoord - plotAreaBounds.getMinY());
    }

    // *************
    // Grid Renderer
    // TODO: should not be a renderer but a property of the Axis? -> allows to configure per axis
    // *************
    private final GridRenderer gridRenderer = new GridRenderer();
    protected final ChangeListener<? super Boolean> gridLineVisibilitychange = (ob, o, n) -> requestLayout();

    public GridRenderer getGridRenderer() {
        return gridRenderer;
    }

    @Deprecated(since = "11.2.0") // set directly in grid renderer or axis
    public final BooleanProperty horizontalGridLinesVisibleProperty() {
        return gridRenderer.horizontalGridLinesVisibleProperty();
    }

    @Deprecated(since = "11.2.0") // set directly in grid renderer or axis
    public final boolean isHorizontalGridLinesVisible() {
        return horizontalGridLinesVisibleProperty().get();
    }

    @Deprecated(since = "11.2.0") // set directly in grid renderer or axis
    public final void setHorizontalGridLinesVisible(final boolean value) {
        horizontalGridLinesVisibleProperty().set(value);
    }

    @Deprecated(since = "11.2.0") // set directly in grid renderer or axis
    public final BooleanProperty verticalGridLinesVisibleProperty() {
        return gridRenderer.verticalGridLinesVisibleProperty();
    }

    @Deprecated(since = "11.2.0") // set directly in grid renderer or axis
    public final boolean isVerticalGridLinesVisible() {
        return verticalGridLinesVisibleProperty().get();
    }

    @Deprecated(since = "11.2.0") // set directly in grid renderer or axis
    public final void setVerticalGridLinesVisible(final boolean value) {
        verticalGridLinesVisibleProperty().set(value);
    }

    // *************
    // Polar Plot handling
    // TODO: handle in special axis/transform
    // *************
    protected final BooleanProperty polarPlot = new SimpleBooleanProperty(this, "polarPlot", false);
    private final ObjectProperty<PolarTickStep> polarStepSize = new SimpleObjectProperty<>(PolarTickStep.THIRTY);

    public final BooleanProperty polarPlotProperty() {
        return polarPlot;
    }

    public final boolean isPolarPlot() {
        return polarPlotProperty().get();
    }

    public final Chart setPolarPlot(final boolean state) {
        polarPlotProperty().set(state);
        return this;
    }

    public ObjectProperty<PolarTickStep> polarStepSizeProperty() {
        return polarStepSize;
    }

    public PolarTickStep getPolarStepSize() {
        return polarStepSizeProperty().get();
    }

    public void setPolarStepSize(final PolarTickStep step) {
        polarStepSizeProperty().set(step);
    }

    // ***************
    // Axes Handling
    // ***************
    public final ObservableList<Axis> axes = FXCollections.observableArrayList();
    protected boolean isAxesUpdate;
    private final io.fair_acc.dataset.event.EventListener axisChangeListener = obs -> FXUtils.runFX(() -> axesInvalidated(obs));
    protected final ListChangeListener<Axis> axesChangeListenerLocal = this::axesChangedLocal;
    protected final ListChangeListener<Axis> axesChangeListener = this::axesChanged;
    private final ChangeListener<Side> axisSideChangeListener = this::axisSideChanged;

    /**
     * Returns the x-axis.
     *
     * @return x axis
     */
    public Axis getXAxis() {
        return getFirstAxis(Orientation.HORIZONTAL);
    }

    /**
     * Returns the y-axis.
     *
     * @return y axis
     */
    public Axis getYAxis() {
        return getFirstAxis(Orientation.VERTICAL);
    }

    /**
     * add Chart specific axis handling (ie. placement around charts, add new DefaultNumericAxis if one is missing,
     * etc.)
     *
     * @param change the new axis change that is being added
     */
    protected void axesChangedLocal(final ListChangeListener.Change<? extends Axis> change) {
        while (change.next()) {
            change.getRemoved().forEach(set -> {
                AssertUtils.notNull("to be removed axis is null", set);
                // remove axis invalidation listener
                set.removeListener(axisChangeListener);
            });
            for (final Axis set : change.getAddedSubList()) {
                // check if axis is associated with an existing renderer,
                // if yes -> throw an exception
                AssertUtils.notNull("to be added axis is null", set);
                set.addListener(axisChangeListener);
            }
        }

        requestLayout();
    }

    public static Axis getFirstAxis(final List<Axis> axes, final Orientation orientation) {
        for (final Axis axis : axes) {
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
     * function called whenever a axis has been invalidated (e.g. range change or parameter plotting changes). Typically
     * calls 'requestLayout()' but can be overwritten in derived classes.
     *
     * @param axisObj the calling axis object
     */
    protected void axesInvalidated(final Object axisObj) {
        if (!(axisObj instanceof Axis) || layoutOngoing || isAxesUpdate) {
            return;
        }
        FXUtils.assertJavaFxThread();
        isAxesUpdate = true;
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("chart axesInvalidated() - called by (1) {}", ProcessingProfiler.getCallingClassMethod(1));
            LOGGER.debug("chart axesInvalidated() - called by (3) {}", ProcessingProfiler.getCallingClassMethod(3));
        }
        requestLayout();
        isAxesUpdate = false;
    }

    public Axis getFirstAxis(final Orientation orientation) {
        return Objects.requireNonNullElseGet(getFirstAxis(getAxes(), orientation), () -> {
            // Add default axis if no suitable axis is available
            var horizontal = orientation == Orientation.HORIZONTAL;
            DefaultNumericAxis newAxis = new DefaultNumericAxis(horizontal ? "x-Axis" : "y-Axis");
            newAxis.setSide(horizontal ? Side.BOTTOM : Side.LEFT);
            newAxis.setDimIndex(horizontal ? DataSet.DIM_X : DataSet.DIM_Y);
            getAxes().add(newAxis);
            return newAxis;
        });
    }

    public ObservableList<Axis> getAxes() {
        return axes;
    }

    protected static void updateNumericAxis(final Axis axis, final List<DataSet> dataSets) {
        if (dataSets == null || dataSets.isEmpty()) {
            return;
        }
        final boolean oldAutoState = axis.autoNotification().getAndSet(false);
        final double oldMin = axis.getAutoRange().getMin();
        final double oldMax = axis.getAutoRange().getMax();
        final double oldLength = axis.getLength();

        final boolean isHorizontal = axis.getSide().isHorizontal();
        final Side side = axis.getSide();
        axis.getAutoRange().clear();
        dataSets.stream().filter(DataSet::isVisible).forEach(dataset -> dataset.lock().readLockGuard(() -> {
            if (dataset.getDimension() > 2 && (side == Side.RIGHT || side == Side.TOP)) {
                if (!dataset.getAxisDescription(DataSet.DIM_Z).isDefined()) {
                    dataset.recomputeLimits(DataSet.DIM_Z);
                }
                axis.getAutoRange().add(dataset.getAxisDescription(DataSet.DIM_Z).getMin());
                axis.getAutoRange().add(dataset.getAxisDescription(DataSet.DIM_Z).getMax());
            } else {
                final int nDim = isHorizontal ? DataSet.DIM_X : DataSet.DIM_Y;
                if (!dataset.getAxisDescription(nDim).isDefined()) {
                    dataset.recomputeLimits(nDim);
                }
                axis.getAutoRange().add(dataset.getAxisDescription(nDim).getMin());
                axis.getAutoRange().add(dataset.getAxisDescription(nDim).getMax());
            }
        }));

        // handling of numeric axis and auto-range or auto-grow setting only
        if (!axis.isAutoRanging() && !axis.isAutoGrowRanging()) {
            if (oldMin != axis.getMin() || oldMax != axis.getMax() || oldLength != axis.getLength()) {
                axis.requestAxisLayout();
            }
            axis.autoNotification().set(oldAutoState);
            return;
        }

        if (axis.isAutoGrowRanging()) {
            axis.getAutoRange().add(oldMin);
            axis.getAutoRange().add(oldMax);
        }

        axis.getAutoRange().setAxisLength(axis.getLength() == 0 ? 1 : axis.getLength(), side);
        axis.getUserRange().setAxisLength(axis.getLength() == 0 ? 1 : axis.getLength(), side);
        axis.invalidateRange(null);

        if (oldMin != axis.getMin() || oldMax != axis.getMax() || oldLength != axis.getLength()) {
            axis.requestAxisLayout();
        }
        axis.autoNotification().set(oldAutoState);
    }
    public void updateAxisRange() {
        if (isDataEmpty()) {
            return;
        }

        // lock datasets to prevent writes while updating the axes
        ObservableList<DataSet> dataSets = this.getAllDatasets();
        // check that all registered data sets have proper ranges defined
        dataSets.parallelStream()
                .forEach(dataset -> dataset.getAxisDescriptions().parallelStream().filter(axisD -> !axisD.isDefined()).forEach(axisDescription -> dataset.lock().writeLockGuard(() -> dataset.recomputeLimits(axisDescription.getDimIndex()))));

        final ArrayDeque<DataSet> lockQueue = new ArrayDeque<>(dataSets);
        recursiveLockGuard(lockQueue, () -> getAxes().forEach(chartAxis -> {
            final List<DataSet> dataSetForAxis = getDataSetForAxis(chartAxis);
            updateNumericAxis(chartAxis, dataSetForAxis);
            // chartAxis.requestAxisLayout()
        }));
    }

    /**
     * add XYChart specific axis handling (ie. placement around charts, add new DefaultNumericAxis if one is missing,
     * etc.)
     *
     * @param change the new axis change that is being added
     */
    protected void axesChanged(final ListChangeListener.Change<? extends Axis> change) {
        while (change.next()) {
            change.getRemoved().forEach(axis -> {
                AssertUtils.notNull("to be removed axis is null", axis);
                axis.sideProperty().removeListener(axisSideChangeListener);
            });

            change.getAddedSubList().forEach(axis -> {
                AssertUtils.notNull("to be added axis is null", axis);
                final Side side = axis.getSide();
                if (side == null) {
                    throw new InvalidParameterException("axis '" + axis.getName() + "' has 'null' as side being set");
                }
                axis.sideProperty().addListener(axisSideChangeListener);
            });
        }
        requestLayout();
    }

    protected void axisSideChanged(final ObservableValue<? extends Side> change, final Side oldValue, final Side newValue) {
        requestLayout();
    }
    protected void recursiveLockGuard(final Deque<DataSet> queue, final Runnable runnable) { // NOPMD
        if (queue.isEmpty()) {
            runnable.run();
        } else {
            queue.pop().lock().readLockGuard(() -> recursiveLockGuard(queue, runnable));
        }
    }

    // ****************
    // Dataset Handling
    // TODO: should this really be handled by the chart?
    // ****************
    private final ObservableList<DataSet> datasets = FXCollections.observableArrayList();
    protected final ObservableList<DataSet> allDataSets = FXCollections.observableArrayList();
    protected final ListChangeListener<DataSet> datasetChangeListener = this::datasetsChanged;
    protected final EventListener dataSetDataListener = obs -> FXUtils.runFX(this::dataSetInvalidated);

    /**
     * @return datasets attached to the chart and datasets attached to all renderers TODO: change to change listener
     *         that add/remove datasets from a global observable list
     */
    public ObservableList<DataSet> getAllShownDatasets() {
        final ObservableList<DataSet> ret = FXCollections.observableArrayList();
        ret.addAll(getDatasets());
        getRenderers().stream().filter(Renderer::showInLegend).forEach(renderer -> ret.addAll(renderer.getDatasets()));
        return ret;
    }

    protected void dataSetInvalidated() {
        // DataSet has notified and invalidate
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("chart dataSetDataListener change notified");
        }
        FXUtils.assertJavaFxThread();
        // updateAxisRange();
        // TODO: check why the following does not always forces a layoutChildren
        requestLayout();
    }

    protected void datasetsChanged(final ListChangeListener.Change<? extends DataSet> change) {
        boolean dataSetChanges = false;
        FXUtils.assertJavaFxThread();
        while (change.next()) {
            for (final DataSet set : change.getRemoved()) {
                // remove Legend listeners from removed datasets
                set.updateEventListener().removeIf(l -> l instanceof DefaultLegend.DatasetVisibilityListener);

                set.removeListener(dataSetDataListener);
                dataSetChanges = true;
            }

            for (final DataSet set : change.getAddedSubList()) {
                set.addListener(dataSetDataListener);
                dataSetChanges = true;
            }
        }

        if (dataSetChanges) {
            if (DEBUG && LOGGER.isDebugEnabled()) {
                LOGGER.debug("chart datasetsChanged(Change) - has dataset changes");
            }
            // updateAxisRange();
            updateLegend(getDatasets(), getRenderers());
            requestLayout();
        }
    }

    /**
     * @return datasets attached to the chart and drawn by all renderers
     */
    public ObservableList<DataSet> getDatasets() {
        return datasets;
    }

    /**
     * @return datasets attached to the chart and datasets attached to all renderers
     */
    public ObservableList<DataSet> getAllDatasets() {
        if (getRenderers() == null) {
            return allDataSets;
        }

        allDataSets.clear();
        allDataSets.addAll(getDatasets());
        getRenderers().stream().filter(renderer -> !(renderer instanceof LabelledMarkerRenderer)).forEach(renderer -> allDataSets.addAll(renderer.getDatasets()));

        return allDataSets;
    }

    private boolean isDataEmpty() {
        return getAllDatasets() == null || getAllDatasets().isEmpty();
    }

    protected List<DataSet> getDataSetForAxis(final Axis axis) {
        final List<DataSet> retVal = new ArrayList<>();
        if (axis == null) {
            return retVal;
        }
        retVal.addAll(getDatasets());
        getRenderers().forEach(renderer -> renderer.getAxes().stream().filter(axis::equals).forEach(rendererAxis -> retVal.addAll(renderer.getDatasets())));
        return retVal;
    }

    /**
     * checks whether renderer has required x and y axes and adds the first x or y from the chart itself if necessary
     * <p>
     * additionally moves axis from Renderer with defined Side that are not yet in the Chart also to the chart's list
     *
     * @param renderer to be checked
     */
    protected void checkRendererForRequiredAxes(final Renderer renderer) {
        if (renderer.getAxes().size() < 2) {
            // not enough axes present in renderer
            Optional<Axis> xAxis = renderer.getAxes().stream().filter(a -> a.getSide().isHorizontal()).findFirst();
            Optional<Axis> yAxis = renderer.getAxes().stream().filter(a -> a.getSide().isVertical()).findFirst();

            // search for horizontal/vertical axes in Chart (which creates one if missing) and add to renderer
            if (xAxis.isEmpty()) {
                renderer.getAxes().add(getFirstAxis(Orientation.HORIZONTAL));
            }
            if (yAxis.isEmpty()) {
                // search for horizontal axis in Chart (which creates one if missing) and add to renderer
                renderer.getAxes().add(getFirstAxis(Orientation.VERTICAL));
            }
        }
        // check if there are assignable axes not yet present in the Chart's list
        getAxes().addAll(renderer.getAxes().stream().limit(2).filter(a -> (a.getSide() != null && !getAxes().contains(a))).collect(Collectors.toList()));
    }

    protected void redrawCanvas() {
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("   chart redrawCanvas() - pre");
        }
        setAutoNotification(false);
        FXUtils.assertJavaFxThread();
        // ensure a maximum refresh rate of 50Hz
        final long now = System.nanoTime();
        final double diffMillisSinceLastUpdate = TimeUnit.NANOSECONDS.toMillis(now - lastCanvasUpdate);
        if (diffMillisSinceLastUpdate < Chart.BURST_LIMIT_MS) {
            if (!callCanvasUpdateLater) {
                callCanvasUpdateLater = true;
                // repaint 20 ms later in case this was just a burst operation
                final KeyFrame kf1 = new KeyFrame(Duration.millis(20), e -> requestLayout());
                final Timeline timeline = new Timeline(kf1);
                Platform.runLater(timeline::play);
            }
            return;
        }
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("   chart redrawCanvas() - executing");
            LOGGER.debug("   chart redrawCanvas() - canvas size = {}", String.format("%fx%f", canvas.getWidth(), canvas.getHeight()));
        }
        lastCanvasUpdate = now;
        callCanvasUpdateLater = false;
        // clear canvas
        final GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (!gridRenderer.isDrawOnTop()) {
            gridRenderer.render(gc, this, 0, null);
        }

        int dataSetOffset = 0;
        for (final Renderer renderer : getRenderers()) {
            // check for and add required axes
            checkRendererForRequiredAxes(renderer);

            final List<DataSet> drawnDataSets = renderer.render(gc, this, dataSetOffset, getDatasets());
            dataSetOffset += drawnDataSets == null ? 0 : drawnDataSets.size();
        }

        if (gridRenderer.isDrawOnTop()) {
            gridRenderer.render(gc, this, 0, null);
        }
        setAutoNotification(true);
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("   xychart redrawCanvas() - done");
        }
    }

    // ********************
    // Layout
    // ********************
    /**
     * isCanvasChangeRequested is a recursion guard to update canvas only once
     */
    protected boolean isCanvasChangeRequested;
    /** layoutOngoing is a recursion guard to update canvas only once */
    protected boolean layoutOngoing;
    private long lastCanvasUpdate;
    private boolean callCanvasUpdateLater;
    private final Canvas canvas = new Canvas();

    @Override
    public void requestLayout() {
        if (DEBUG && LOGGER.isDebugEnabled()) {
            // normal debugDepth = 1 but for more verbose logging (e.g. recursion) use > 10
            for (int debugDepth = 1; debugDepth < 2; debugDepth++) {
                LOGGER.atDebug().addArgument(debugDepth).addArgument(ProcessingProfiler.getCallingClassMethod(debugDepth)).log("chart requestLayout() - called by {}: {}");
            }
            LOGGER.atDebug().addArgument("[..]").log("chart requestLayout() - called by {}");
        }
        FXUtils.assertJavaFxThread();
        super.requestLayout();
    }

    @Override
    protected void layoutChildren() {
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("chart layoutChildren() - pre");
        }
        if (layoutOngoing) {
            return;
        }
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("chart layoutChildren() - execute");
        }
        final long start = ProcessingProfiler.getTimeStamp();
        layoutOngoing = true;

        plotBackground.resizeRelocate(0, 0, getWidth(), getHeight());
        plotForeGround.resizeRelocate(0, 0, getWidth(), getHeight());

        double marginTop = 0;
        double marginBottom = 0;           // reset bounds of all axes?
        double marginLeft = 0;             // evaluate bounds for axes
        double marginRight = 0;            // iterate on all renderers and update ranges for their axes according to datasets
        double horizontalCenterHeight = 0; // evaluate linked axes?
        double verticalCenterWidth = 0;
        double posTop = 0;

        // legend
        if (legendVisible.get()){
            final var legendNode = legend.get().getNode();
            switch (legendSide.get()) {
                case TOP:
                    legendNode.resizeRelocate(marginLeft, marginTop, getWidth() - marginLeft - marginRight, legendNode.prefHeight(getWidth()));
                    marginTop += legendNode.prefHeight(getWidth());
                    posTop += legendNode.prefHeight(getWidth());
                    break;
                case BOTTOM:
                    marginBottom += legendNode.prefHeight(getWidth());
                    legendNode.resizeRelocate(marginLeft, getHeight() - marginBottom, getWidth() - marginLeft - marginRight, legendNode.prefHeight(getWidth()));
                    break;
                case LEFT:
                    break;
                case RIGHT:
                    break;
                case CENTER_HOR:
                case CENTER_VER:
                    LOGGER.atError().log("Invalid Legend Position");
                    break;
            }
        }

        // update axes range first because this may change the overall layout
        updateAxisRange();
        ProcessingProfiler.getTimeDiff(start, "updateAxisRange()");

        // layout all axes around the borders of the chart
        // get sizes for all axes
        marginTop += axes.stream().filter(ax -> ax.getSide() == Side.TOP).mapToDouble(ax -> ((Node) ax).prefHeight(getWidth())).sum();
        marginBottom += axes.stream().filter(ax -> ax.getSide() == Side.BOTTOM).mapToDouble(ax -> ((Node) ax).prefHeight(getWidth())).sum();
        marginLeft += axes.stream().filter(ax -> ax.getSide() == Side.LEFT).mapToDouble(ax -> ((Node) ax).prefWidth(getHeight())).sum();
        marginRight += axes.stream().filter(ax -> ax.getSide() == Side.RIGHT).mapToDouble(ax -> ((Node) ax).prefWidth(getHeight())).sum();
        horizontalCenterHeight += axes.stream().filter(ax -> ax.getSide() == Side.CENTER_HOR).mapToDouble(ax -> ((Node) ax).prefHeight(getWidth())).sum();
        verticalCenterWidth += axes.stream().filter(ax -> ax.getSide() == Side.CENTER_VER).mapToDouble(ax -> ((Node) ax).prefWidth(getHeight())).sum();

        // space for other elements: toolbar
        final var toolbarWidth = toolBar.prefWidth(toolBarPane.prefHeight(getWidth()));
        final var toolbarSpace = getWidth() - marginLeft - marginRight - toolbarWidth;
        toolBarPane.resizeRelocate(marginLeft + toolbarSpace > 0 ? toolbarSpace / 2.0 : 0, marginTop, getWidth() - marginLeft - marginRight - (toolbarSpace > 0 ? toolbarSpace : 0), toolBarPane.prefHeight(getWidth()));
        // overlayed over canvas, was: marginTop += toolBarPane.prefHeight(getWidth());

        // resize canvas to the remaining space: canvas is not resizeable -> set size explicitly (is this the correct way to go?)
        // canvas.resizeRelocate(marginLeft, marginTop, getWidth() - marginLeft - marginRight, getHeight() - marginTop - marginBottom);
        canvas.relocate(marginLeft, marginTop);
        canvas.setWidth(getWidth() - marginLeft - marginRight);
        canvas.setHeight(getHeight() - marginTop - marginBottom);
        canvasForeground.resizeRelocate(marginLeft, marginTop, getWidth() - marginLeft - marginRight, getHeight() - marginTop - marginBottom);

        // layout axes
        positionAxes(marginTop, marginBottom, marginLeft, marginRight, horizontalCenterHeight, verticalCenterWidth, posTop);
        // actually draw the axes (tick marks and all)
        axes.forEach(ax -> {
            ax.requestAxisLayout();
        });
        // draw grid below?

        // request re-layout of canvas (TODO: only if canvas size changed?)
        redrawCanvas();
        ProcessingProfiler.getTimeDiff(start, "updateCanvas()");

        // draw grid above?

        // request re-layout of plugins
        layoutPluginsChildren();
        ProcessingProfiler.getTimeDiff(start, "layoutPluginsChildren()");

        ProcessingProfiler.getTimeDiff(start, "end");

        layoutOngoing = false; // why is this needed? layout children should only be called by the
        if (DEBUG && LOGGER.isDebugEnabled()) {
            LOGGER.debug("chart layoutChildren() - done");
        }
        fireInvalidated();
    }

    private void positionAxes(final double marginTop, final double marginBottom, final double marginLeft, final double marginRight, final double horizontalCenterHeight, final double verticalCenterWidth, double posTop) {
        double posBottom = getHeight();
        double posHorCenter = (getHeight() - marginTop - marginBottom + horizontalCenterHeight) / 2.0 + marginTop;
        double posLeft = 0;
        double posRight = getWidth();
        double posVertCenter = (getWidth() - marginLeft - marginRight - verticalCenterWidth) / 2.0 + marginLeft;
        for (final var axis_ : axes) {
            final var axis = (AbstractAxis) axis_;
            switch (axis.getSide()) {
                case TOP:
                    axis.resizeRelocate(marginLeft, posTop,getWidth() - marginLeft - marginRight, axis.prefHeight(getWidth()));
                    posTop += axis.prefHeight(getWidth());
                    break;
                case BOTTOM:
                    posBottom -= axis.prefHeight(getWidth());
                    axis.resizeRelocate(marginLeft, posBottom, getWidth() - marginLeft - marginRight, axis.prefHeight(getWidth()));
                    break;
                case LEFT:
                    axis.resizeRelocate(posLeft, marginTop, axis.prefWidth(getHeight()), getHeight() - marginTop - marginBottom);
                    posLeft += axis.prefWidth(getHeight());
                    break;
                case RIGHT:
                    posRight -= axis.prefWidth(getHeight());
                    axis.resizeRelocate(posRight, marginTop, axis.prefWidth(getHeight()), getHeight() - marginTop - marginBottom);
                    break;
                case CENTER_HOR:
                    posHorCenter -= axis.prefHeight(getWidth());
                    axis.resizeRelocate(marginLeft, posHorCenter, getWidth() - marginLeft - marginRight, axis.prefHeight(getWidth()));
                    break;
                case CENTER_VER:
                    axis.resizeRelocate(posVertCenter, marginTop, axis.prefWidth(getHeight()), getHeight() - marginTop - marginBottom);
                    posVertCenter += axis.prefWidth(getHeight());
                    break;
            }
        }
    }
}
