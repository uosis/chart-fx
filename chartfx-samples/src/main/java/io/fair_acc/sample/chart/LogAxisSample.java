package io.fair_acc.sample.chart;

import io.fair_acc.chartfx.Chart;
import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.plugins.EditAxis;
import io.fair_acc.chartfx.plugins.Zoomer;
import io.fair_acc.dataset.spi.DoubleDataSet;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 *
 * Simple example of chart with log axis
 *
 * @author rstein
 *
 */
public class LogAxisSample extends ChartSample {
    private static final int N_SAMPLES = 1000;

    @Override
    public Node getChartPanel(final Stage primaryStage) {
        final StackPane root = new StackPane();
        DefaultNumericAxis xAxis = new DefaultNumericAxis();
        DefaultNumericAxis yAxis = new DefaultNumericAxis();
        yAxis.setLogAxis(true);
        // yAxis.setLogarithmBase(2);

        final Chart chart = new Chart();
        chart.getAxes().addAll(xAxis, yAxis);
        final Zoomer zoomer = new Zoomer();
        zoomer.setPannerEnabled(false); // not recommended (ie. Axes do not support complex numbers, e.g. 'log(-1)')
        chart.getPlugins().add(zoomer); // zoom around
        chart.getPlugins().add(new EditAxis()); // manually modify axis

        root.getChildren().add(chart);

        final DoubleDataSet dataSet1 = new DoubleDataSet("data set #1");
        final DoubleDataSet dataSet2 = new DoubleDataSet("data set #2");
        final DoubleDataSet dataSet3 = new DoubleDataSet("data set #2");
        chart.getDatasets().addAll(dataSet1, dataSet2, dataSet3);

        // classic way of adding data points
        // N.B. in a life-update context every new points triggers a chart
        // repaint. This can be suppressed by adding/setting full arrays and/or
        // by selecting dataSet1.setAutoNotification(false/true) for the data
        // sets (or chart) concerned to suppress this repaint.
        dataSet1.autoNotification().set(false);
        dataSet2.autoNotification().set(false);
        dataSet3.autoNotification().set(false);
        for (int n = 0; n < N_SAMPLES; n++) {
            final double x = n + 1.0;
            double y = 0.01 * (n + 1);

            dataSet1.add(x, 2.0 * x);
            dataSet2.add(x, Math.pow(2, y));
            dataSet3.add(x, Math.exp(y));
        }
        dataSet1.autoNotification().set(true);
        dataSet2.autoNotification().set(true);
        dataSet3.autoNotification().set(true);

        return root;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}