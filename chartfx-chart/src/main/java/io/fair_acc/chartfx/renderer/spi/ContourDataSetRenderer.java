package io.fair_acc.chartfx.renderer.spi;

import static javafx.scene.paint.CycleMethod.NO_CYCLE;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import io.fair_acc.chartfx.Chart;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.XYChart;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fair_acc.chartfx.axes.Axis;
import io.fair_acc.chartfx.axes.AxisTransform;
import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.plugins.Zoomer;
import io.fair_acc.chartfx.renderer.Renderer;
import io.fair_acc.chartfx.renderer.spi.hexagon.Hexagon;
import io.fair_acc.chartfx.renderer.spi.hexagon.HexagonMap;
import io.fair_acc.chartfx.renderer.spi.marchingsquares.GeneralPath;
import io.fair_acc.chartfx.renderer.spi.marchingsquares.MarchingSquares;
import io.fair_acc.chartfx.renderer.spi.utils.ColorGradient;
import io.fair_acc.chartfx.ui.geometry.Side;
import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.GridDataSet;
import io.fair_acc.dataset.utils.ProcessingProfiler;

/**
 * Default renderer for the display of 3D surface-type DataSets.
 * The following drawing options controlled via {@link #setContourType(io.fair_acc.chartfx.renderer.ContourType)} are provided:
 * <ul>
 * <li>CONTOUR: marching-square based contour plotting algorithm, see e.g.
 * <a href="https://en.wikipedia.org/wiki/Marching_squares#Isoline">reference</a>
 * <li>CONTOUR_FAST: an experimental contour plotting algorithm,
 * <li>CONTOUR_HEXAGON: a hexagon-map based contour plotting algorithm,
 * <li>HEATMAP: an 2D orthogonal projection based plotting algorithm
 * <li>HEATMAP_HEXAGON: an 2D orthogonal hexagon-projection based plotting algorithm.
 * </ul>
 * Most of the internal processing algorithm are parallelised which can be controlled via
 * {@link #setParallelImplementation(boolean)}. A data reduction is performed in case the number of data points exceed
 * the the underlying number Canvas pixels number in order to improve efficiency and required texture GPU buffer. This
 * data reduction is controlled via {@link #setPointReduction(boolean)} and the reduction type (MIN, MAX, AVERAGE,
 * DOWN_SAMPLE) via {@link #setReductionType}, and the {@link #setReductionFactorX(int)} and
 * {@link #setReductionFactorY(int)} functions.
 * N.B. Regarding implementation of user-level DataSet interfaces: While the DataSet3D::getZ(int) and
 * DataSet::get(DIM_Z, int) routines should match, the DataSet3D is considered a convenience interface primarily to be
 * used for external user-level code.
 * This renderer primarily relies for performance reasons internally on the more generic DataSet::get(DIM_Z, int index)
 * interface. DataSet::get(DIM_Z, int index) is assumed to be a row-major ordered matrix with the (0, 0) coordinate (ie.
 * first index get(DIM_Z, 0)) being drawn at the bottom left corner of the canvas for non-inverted axes.
 *
 * @author rstein
 */
public class ContourDataSetRenderer extends AbstractContourDataSetRendererParameter<ContourDataSetRenderer> implements Renderer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContourDataSetRenderer.class);
    private ContourDataSetCache localCache;
    private Axis zAxis;
    protected final Rectangle gradientRect = new Rectangle();

    private void drawContour(final GraphicsContext gc, final ContourDataSetCache lCache) {
        final double[] levels = new double[getNumberQuantisationLevels()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (i + 1) / (double) levels.length;
        }

        final int xSize = lCache.xSize;
        final int ySize = lCache.ySize;
        final double[][] data = new double[ySize][xSize];
        for (int yIndex = 0; yIndex < ySize; yIndex++) {
            if (xSize >= 0)
                System.arraycopy(lCache.reduced, yIndex * xSize + 0, data[ySize - 1 - yIndex], 0, xSize);
        }

        // abort if min/max == 0 -> cannot compute contours
        final double zRange = Math.abs(lCache.zMax - lCache.zMin);
        if (zRange <= 0) {
            return;
        }

        final ColorGradient colorGradient = getColorGradient();
        final MarchingSquares marchingSquares = new MarchingSquares();
        final double scaleX = lCache.xDataPixelRange / xSize;
        final double scaleY = lCache.yDataPixelRange / ySize;
        gc.save();
        gc.translate(lCache.xDataPixelMin, lCache.yDataPixelMin);
        gc.scale(scaleX, scaleY);
        final GeneralPath[] isolines;
        try {
            isolines = marchingSquares.buildContours(data, levels);
            int levelCount = 0;
            for (final GeneralPath path : isolines) {
                if (path.size() > getMaxContourSegments()) {
                    levelCount++;
                    continue;
                }
                final Color color = lCache.zInverted ? colorGradient.getColor(1 - levels[levelCount++]) : colorGradient.getColor(levels[levelCount++]);
                gc.setStroke(color);
                gc.setLineDashes(1.0);
                gc.setMiterLimit(10);
                gc.setFill(color);
                gc.setLineWidth(0.5);
                path.draw(gc);
            }
        } catch (InterruptedException | ExecutionException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.atError().setCause(e).log("marchingSquares algorithm");
            }
        } finally {
            gc.restore();
        }
    }

    private void drawContourFast(final GraphicsContext gc, final AxisTransform axisTransform, final ContourDataSetCache lCache) {
        final long start = ProcessingProfiler.getTimeStamp();
        final int xSize = lCache.xSize;
        final int ySize = lCache.ySize;
        final double zMin = axisTransform.forward(lCache.zMin);
        final double zMax = axisTransform.forward(lCache.zMax);

        // N.B. works only since OpenJFX 12!! fall-back for JDK8 is the old implementation
        gc.setImageSmoothing(isSmooth());

        getNumberQuantisationLevels();

        // filter for contour
        final double[][] input = new double[xSize][ySize];
        final double[][] output = new double[xSize][ySize];
        final double[][] output2 = new double[xSize][ySize];

        // setup quantisation levels
        final double[] levels = new double[getNumberQuantisationLevels()];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (i + 1) / (double) levels.length;
        }

        final int length = xSize * ySize;
        for (int i = 0; i < length; i++) {
            final int x = i % xSize;
            final int y = i / xSize;
            input[x][y] = lCache.reduced[i];
        }

        final WritableImage image = localCache.getImage(xSize, ySize);
        final PixelWriter pixelWriter = image.getPixelWriter();
        if (pixelWriter == null) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.atError().log("Could not get PixelWriter for image");
            }
            return;
        }

        final ColorGradient colorGradient = getColorGradient();
        for (final double level : levels) {
            ContourDataSetRenderer.sobelOperator(input, output2, zMin, zMax, level);
            ContourDataSetRenderer.erosionOperator(output2, output, zMin, zMax, level);

            for (int yIndex = 0; yIndex < ySize; yIndex++) {
                final int yIndex2 = ySize - 1 - yIndex;
                for (int xIndex = 0; xIndex < xSize; xIndex++) {
                    final double z = output[xIndex][yIndex];

                    if (z <= 0) {
                        continue;
                    }
                    Color color = lCache.zInverted ? colorGradient.getColor(1 - level) : colorGradient.getColor(level);

                    pixelWriter.setColor(xIndex, yIndex2, color);
                }
            }
        }

        gc.drawImage(image, lCache.xDataPixelMin, lCache.yDataPixelMin, lCache.xDataPixelRange, lCache.yDataPixelRange);

        localCache.add(image);
        ProcessingProfiler.getTimeDiff(start, "sobel");
    }

    private void drawHeatMap(final GraphicsContext gc, final ContourDataSetCache lCache) {
        final long start = ProcessingProfiler.getTimeStamp();

        // N.B. works only since OpenJFX 12!! fall-back for JDK8 is the old implementation
        gc.setImageSmoothing(isSmooth());

        // process z quantisation to colour transform
        final WritableImage image = localCache.convertDataArrayToImage(lCache.reduced, lCache.xSize, lCache.ySize, getColorGradient());
        ProcessingProfiler.getTimeDiff(start, "color map");

        gc.drawImage(image, lCache.xDataPixelMin, lCache.yDataPixelMin, lCache.xDataPixelRange, lCache.yDataPixelRange);

        localCache.add(image);
        ProcessingProfiler.getTimeDiff(start, "drawHeatMap");
    }

    private void drawHexagonHeatMap(final GraphicsContext gc, final ContourDataSetCache lCache) {
        final long start = ProcessingProfiler.getTimeStamp();

        // process z quantisation to colour transform
        final WritableImage image = localCache.convertDataArrayToImage(lCache.reduced, lCache.xSize, lCache.ySize, getColorGradient());

        final int tileSize = Math.max(getMinHexTileSizeProperty(), (int) lCache.xAxisWidth / lCache.xSize);
        final int nWidthInTiles = (int) (lCache.xAxisWidth / (tileSize * Math.sqrt(3))) + 1;

        final HexagonMap map2 = new HexagonMap(tileSize, image, nWidthInTiles, (q, r, imagePixelColor, map) -> {
            final Hexagon h = new Hexagon(q, r);
            h.setFill(imagePixelColor);
            h.setStroke(imagePixelColor);

            h.setStrokeWidth(0.5);
            map.addHexagon(h);
        });
        localCache.add(image);

        ProcessingProfiler.getTimeDiff(start, "drawHexagonMap - prepare");
        final double scaleX = lCache.xDataPixelRange / lCache.xAxisWidth;
        final double scaleY = lCache.yDataPixelRange / lCache.yAxisHeight;

        gc.save();
        gc.translate(lCache.xDataPixelMin, lCache.yDataPixelMin);
        gc.scale(scaleX, scaleY);

        map2.render(gc.getCanvas());
        gc.restore();

        ProcessingProfiler.getTimeDiff(start, "drawHexagonMap");
    }

    private void drawHexagonMapContour(final GraphicsContext gc, final ContourDataSetCache lCache) {
        final long start = ProcessingProfiler.getTimeStamp();

        // process z quantisation to colour transform
        final WritableImage image = localCache.convertDataArrayToImage(lCache.reduced, lCache.xSize, lCache.ySize, getColorGradient());

        final int tileSize = Math.max(getMinHexTileSizeProperty(), (int) lCache.xAxisWidth / lCache.xSize);
        final int nWidthInTiles = (int) (lCache.xAxisWidth / (tileSize * Math.sqrt(3)));
        final HexagonMap hexMap = new HexagonMap(tileSize, image, nWidthInTiles, (q, r, imagePixelColor, map) -> {
            final Hexagon h = new Hexagon(q, r);
            h.setFill(Color.TRANSPARENT); // contour being plotted
            h.setStroke(imagePixelColor);
            h.setStrokeType(StrokeType.CENTERED);

            h.setStrokeWidth(1);
            map.addHexagon(h);
        });
        localCache.add(image);

        ProcessingProfiler.getTimeDiff(start, "drawHexagonMapContour - prepare");

        final double scaleX = lCache.xDataPixelRange / lCache.xAxisWidth;
        final double scaleY = lCache.yDataPixelRange / lCache.yAxisHeight;
        gc.save();
        gc.translate(lCache.xDataPixelMin, lCache.yDataPixelMin);
        gc.scale(scaleX, scaleY);

        hexMap.renderContour(gc.getCanvas());
        gc.restore();

        ProcessingProfiler.getTimeDiff(start, "drawHexagonMapContour");
    }

    @Override
    public Canvas drawLegendSymbol(DataSet dataSet, int dsIndex, int width, int height) {
        return null; // TODO: implement
    }

    /**
     * @return the instance of this ContourDataSetRenderer.
     */
    @Override
    protected ContourDataSetRenderer getThis() {
        return this;
    }

    public Axis getZAxis() {
        final ArrayList<Axis> localAxesList = new ArrayList<>(getAxes());
        localAxesList.remove(getFirstAxis(Orientation.HORIZONTAL));
        localAxesList.remove(getFirstAxis(Orientation.VERTICAL));
        if (localAxesList.isEmpty()) {
            zAxis = new DefaultNumericAxis("z-Axis");
            zAxis.setAnimated(false);
            zAxis.setSide(Side.RIGHT);
            getAxes().add(zAxis);
        } else {
            zAxis = localAxesList.get(0);
            if (zAxis.getSide() == null) {
                zAxis.setSide(Side.RIGHT);
            }
        }
        // small cosmetic change to have colour gradient at the utmost right
        // position
        shiftZAxisToRight();
        return zAxis;
    }

    protected void layoutZAxis(final Axis localZAxis) {
        if (localZAxis.getSide() == null || !(localZAxis instanceof Node)) {
            return;
        }
        Node zAxisNode = (Node) localZAxis;
        zAxisNode.getProperties().put(Zoomer.ZOOMER_OMIT_AXIS, Boolean.TRUE);

        if (localZAxis.getSide().isHorizontal()) {
            zAxisNode.setLayoutX(50);
            gradientRect.setX(0);
            gradientRect.setWidth(localZAxis.getWidth());
            gradientRect.setHeight(20);
            zAxisNode.setLayoutX(0);
            gradientRect.setFill(new LinearGradient(0, 0, 1, 0, true, NO_CYCLE, getColorGradient().getStops()));

            if (!(zAxisNode.getParent() instanceof VBox)) {
                return;
            }
            final VBox parent = (VBox) zAxisNode.getParent();
            if (!parent.getChildren().contains(gradientRect)) {
                parent.getChildren().add(gradientRect);
            }
        } else {
            zAxisNode.setLayoutY(50);
            gradientRect.setWidth(20);
            gradientRect.setHeight(localZAxis.getHeight());
            gradientRect.setFill(new LinearGradient(0, 1, 0, 0, true, NO_CYCLE, getColorGradient().getStops()));
            gradientRect.setLayoutX(10);

            if (!(zAxisNode.getParent() instanceof HBox)) {
                return;
            }
            final HBox parent = (HBox) zAxisNode.getParent();
            if (!parent.getChildren().contains(gradientRect)) {
                parent.getChildren().add(0, gradientRect);
            }
        }

        if (localZAxis instanceof Region) {
            ((Region) zAxisNode).requestLayout();
        }
    }

    private void paintCanvas(final GraphicsContext gc) {
        if (localCache.xSize == 0 || localCache.ySize == 0) {
            return;
        }

        final Axis localZAxis = getZAxis();
        if (localZAxis == null) {
            return;
        }
        final AxisTransform axisTransform = localZAxis.getAxisTransform();
        if (axisTransform == null) {
            return;
        }
        switch (getContourType()) {
        case CONTOUR:
            drawContour(gc, localCache);
            break;
        case CONTOUR_FAST:
            drawContourFast(gc, axisTransform, localCache);
            break;
        case CONTOUR_HEXAGON:
            drawHexagonMapContour(gc, localCache);
            break;
        case HEATMAP_HEXAGON:
            drawHexagonHeatMap(gc, localCache);
            break;
        case HEATMAP:
        default:
            drawHeatMap(gc, localCache);
            break;
        }
    }

    @Override
    public List<DataSet> render(final GraphicsContext gc, final Chart chart, final int dataSetOffset,
                                final ObservableList<DataSet> datasets) {
        final long start = ProcessingProfiler.getTimeStamp();
        if (chart == null) {
            throw new InvalidParameterException("must be derivative of XYChart for renderer - " + this.getClass().getSimpleName());
        }

        // make local copy and add renderer specific data sets
        final List<DataSet> localDataSetList = new ArrayList<>(datasets);
        localDataSetList.addAll(getDatasets());

        // If there are no data sets
        if (localDataSetList.isEmpty()) {
            return Collections.emptyList();
        }

        long mid = ProcessingProfiler.getTimeDiff(start, "init");
        // N.B. importance of reverse order: start with last index, so that
        // most(-like) important DataSet is drawn on
        // top of the others

        List<DataSet> drawnDataSet = new ArrayList<>(localDataSetList.size());
        for (int dataSetIndex = localDataSetList.size() - 1; dataSetIndex >= 0; dataSetIndex--) {
            final DataSet dataSet = localDataSetList.get(dataSetIndex);
            if (!dataSet.isVisible() || !(dataSet instanceof GridDataSet) || dataSet.getDimension() <= 2) {
                continue; // DataSet not applicable to ContourChartRenderer
            }

            final boolean result = dataSet.lock().readLockGuard(() -> {
                long stop = ProcessingProfiler.getTimeDiff(mid, "dataSet.lock()");

                if (dataSet.getDataCount() == 0) {
                    return false;
                }

                localCache = new ContourDataSetCache(chart, this, dataSet); // NOPMD
                ProcessingProfiler.getTimeDiff(stop, "updateCachedVariables");
                return true;
            });

            if (result) {
                layoutZAxis(getZAxis());
                // data reduction algorithm here
                paintCanvas(gc);
                drawnDataSet.add(dataSet);
                localCache.releaseCachedVariables();
            }

            ProcessingProfiler.getTimeDiff(mid, "finished drawing");

        } // end of 'dataSetIndex' loop

        ProcessingProfiler.getTimeDiff(start);

        return drawnDataSet;
    }

    public void shiftZAxisToLeft() {
        gradientRect.toBack();
        if (zAxis instanceof Node) {
            ((Node) zAxis).toBack();
        }
    }

    public void shiftZAxisToRight() {
        gradientRect.toFront();
        if (zAxis instanceof Node) {
            ((Node) zAxis).toFront();
        }
    }

    public static double convolution(final double[][] pixelMatrix) {
        final double gy = pixelMatrix[0][0] * -1 + pixelMatrix[0][1] * -2 + pixelMatrix[0][2] * -1 + pixelMatrix[2][0] + pixelMatrix[2][1] * 2
                        + pixelMatrix[2][2] * 1;
        final double gx = pixelMatrix[0][0] + pixelMatrix[0][2] * -1 + pixelMatrix[1][0] * 2 + pixelMatrix[1][2] * -2 + pixelMatrix[2][0]
                        + pixelMatrix[2][2] * -1;
        return Math.sqrt(Math.pow(gy, 2) + Math.pow(gx, 2));
    }

    public static double erosionConvolution(final double[][] pixelMatrix) {
        double sum = 0.0;
        sum += pixelMatrix[0][0];
        sum += pixelMatrix[0][1];
        sum += pixelMatrix[0][2];
        sum += pixelMatrix[1][0];
        sum += pixelMatrix[1][1];
        sum += pixelMatrix[1][2];
        sum += pixelMatrix[2][0];
        sum += pixelMatrix[2][1];
        sum += pixelMatrix[2][2];
        return sum;
    }

    private static void erosionOperator(final double[][] input, final double[][] output, final double zMin, final double zMax, final double level) {
        final int width = input.length;
        final int height = input[0].length;
        final double[][] gX = new double[width][height];
        final double[][] gY = new double[width][height];

        final double[][] pixelMatrix = new double[3][3];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (i == 0 || i == width - 1 || j == 0 || j == height - 1) {
                    gX[i][j] = gY[i][j] = output[i][j] = 0;
                } else {
                    pixelMatrix[0][0] = input[i - 1][j - 1] > level ? 1.0 : 0.0;
                    pixelMatrix[0][1] = input[i - 1][j] > level ? 1.0 : 0.0;
                    pixelMatrix[0][2] = input[i - 1][j + 1] > level ? 1.0 : 0.0;
                    pixelMatrix[1][0] = input[i][j - 1] > level ? 1.0 : 0.0;
                    pixelMatrix[1][2] = input[i][j + 1] > level ? 1.0 : 0.0;
                    pixelMatrix[2][0] = input[i + 1][j - 1] > level ? 1.0 : 0.0;
                    pixelMatrix[2][1] = input[i + 1][j] > level ? 1.0 : 0.0;
                    pixelMatrix[2][2] = input[i + 1][j + 1] > level ? 1.0 : 0.0;

                    final double zNorm = ContourDataSetRenderer.erosionConvolution(pixelMatrix);
                    output[i][j] = zNorm > 4 ? 1.0 : 0.0;
                }
            }
        }
    }

    private static double quantize(final double value, final int nLevels) {
        return ((int) (value * nLevels)) / (double) nLevels;
        // original: return Math.round(value * nLevels) / (double) nLevels;
    }

    private static void sobelOperator(final double[][] input, final double[][] output, final double zMin, final double zMax, final double level) {
        final int width = input.length;
        final int height = input[0].length;
        final double[][] gX = new double[width][height];
        final double[][] gY = new double[width][height];

        final double[][] pixelMatrix = new double[3][3];
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (i == 0 || i == width - 1 || j == 0 || j == height - 1) {
                    gX[i][j] = gY[i][j] = output[i][j] = 0;
                } else {
                    // Roberts Cross
                    // gX[i][j] = -1.0 * input[i][j - 1] - 0.0 * input[i][j] + 0.0 * input[i][j + 1];
                    // gX[i][j] += 0.0 * input[i][j - 1] + 1.0 * input[i][j] + 0.0 * input[i + 1][j + 1];
                    //
                    // gY[i][j] = 0.0 * input[i][j - 1] - 1.0 * input[i][j] + 0.0 * input[i][j + 1];
                    // gY[i][j] += 1.0 * input[i][j - 1] - 0.0 * input[i][j] + 0.0 * input[i + 1][j + 1];
                    //
                    // double zNorm = Math.abs(gX[i][j]) + Math.abs(gY[i][j]);

                    pixelMatrix[0][0] = input[i - 1][j - 1] > level ? 1.0 : 0.0;
                    pixelMatrix[0][1] = input[i - 1][j] > level ? 1.0 : 0.0;
                    pixelMatrix[0][2] = input[i - 1][j + 1] > level ? 1.0 : 0.0;
                    pixelMatrix[1][0] = input[i][j - 1] > level ? 1.0 : 0.0;
                    pixelMatrix[1][2] = input[i][j + 1] > level ? 1.0 : 0.0;
                    pixelMatrix[2][0] = input[i + 1][j - 1] > level ? 1.0 : 0.0;
                    pixelMatrix[2][1] = input[i + 1][j] > level ? 1.0 : 0.0;
                    pixelMatrix[2][2] = input[i + 1][j + 1] > level ? 1.0 : 0.0;

                    output[i][j] = ContourDataSetRenderer.convolution(pixelMatrix); // > level ? 1.0 : 0.0;
                }
            }
        }
    }
}
