package io.fair_acc.sample.chart;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.fair_acc.chartfx.Chart;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import io.fair_acc.chartfx.axes.AxisLabelOverlapPolicy;
import io.fair_acc.chartfx.axes.spi.CategoryAxis;
import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.plugins.EditAxis;
import io.fair_acc.chartfx.plugins.ParameterMeasurements;
import io.fair_acc.chartfx.plugins.Zoomer;
import io.fair_acc.chartfx.renderer.LineStyle;
import io.fair_acc.chartfx.renderer.spi.ErrorDataSetRenderer;
import io.fair_acc.dataset.spi.DefaultErrorDataSet;
import io.fair_acc.dataset.testdata.spi.RandomDataGenerator;

/**
 * @author rstein
 */
public class CategoryAxisSample extends ChartSample {
    private static final int N_SAMPLES = 30;

    @Override
    public Node getChartPanel(final Stage primaryStage) {
        final StackPane root = new StackPane();
        final CategoryAxis xAxis = new CategoryAxis("months");
        // xAxis.setTickLabelRotation(90);
        // alt:
        xAxis.setOverlapPolicy(AxisLabelOverlapPolicy.SHIFT_ALT);
        xAxis.setMaxMajorTickLabelCount(N_SAMPLES + 1);
        final DefaultNumericAxis yAxis = new DefaultNumericAxis("yAxis");

        final Chart lineChartPlot = new Chart();
        lineChartPlot.getAxes().addAll(xAxis, yAxis);
        // set them false to make the plot faster
        lineChartPlot.setAnimated(false);
        lineChartPlot.getRenderers().clear();
        // lineChartPlot.getRenderers().add(new ReducingLineRenderer());
        final ErrorDataSetRenderer renderer = new ErrorDataSetRenderer();
        renderer.setPolyLineStyle(LineStyle.NORMAL);
        renderer.setPolyLineStyle(LineStyle.HISTOGRAM);
        lineChartPlot.getRenderers().add(renderer);
        lineChartPlot.legendVisibleProperty().set(true);

        lineChartPlot.getPlugins().add(new ParameterMeasurements());
        lineChartPlot.getPlugins().add(new EditAxis());
        final Zoomer zoomer = new Zoomer();
        // zoomer.setSliderVisible(false);
        // zoomer.setAddButtonsToToolBar(false);
        lineChartPlot.getPlugins().add(zoomer);

        final DefaultErrorDataSet dataSet = new DefaultErrorDataSet("myData");

        final DateFormatSymbols dfs = new DateFormatSymbols(Locale.ENGLISH);
        final List<String> categories = new ArrayList<>(Arrays.asList(Arrays.copyOf(dfs.getShortMonths(), 12)));
        for (int i = categories.size(); i < CategoryAxisSample.N_SAMPLES; i++) {
            categories.add("Month" + (i + 1));
        }

        // setting the category via axis forces the axis' category
        // N.B. disable this if you want to use the data set's categories
        xAxis.setCategories(categories);

        double y = 0;
        for (int n = 0; n < CategoryAxisSample.N_SAMPLES; n++) {
            y += RandomDataGenerator.random() - 0.5;
            final double ex = 0.0;
            final double ey = 0.1;
            dataSet.add(n, y, ex, ey);
            dataSet.addDataLabel(n, "SpecialCategory#" + n);
        }

        // setting the axis categories to null forces the first data set's
        // category
        // enable this if you want to use the data set's categories
        // xAxis.setCategories(null);

        lineChartPlot.getDatasets().add(dataSet);
        root.getChildren().add(lineChartPlot);

        return root;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}