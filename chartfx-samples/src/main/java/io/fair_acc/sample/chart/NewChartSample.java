package io.fair_acc.sample.chart;

import io.fair_acc.chartfx.Chart;
import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.renderer.Renderer;
import io.fair_acc.chartfx.renderer.spi.ReducingLineRenderer;
import io.fair_acc.chartfx.ui.geometry.Side;
import io.fair_acc.dataset.DataSet;
import io.fair_acc.dataset.testdata.spi.SineFunction;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class NewChartSample extends ChartSample {
    @Override
    public Node getChartPanel(final Stage primaryStage) {
        Chart chart = new Chart();
        var xAxis1 = new DefaultNumericAxis("xAxis1");
        var xAxis2 = new DefaultNumericAxis("xAxis2");
        var xAxis3 = new DefaultNumericAxis("xAxis3");
        var yAxis1 = new DefaultNumericAxis("yAxis1");
        var yAxis2 = new DefaultNumericAxis("yAxis2");
        var yAxis3 = new DefaultNumericAxis("yAxis3");
        xAxis1.setStyle("-fx-background-color: green;");
        xAxis2.setStyle("-fx-background-color: cyan;");
        xAxis3.setStyle("-fx-background-color: orange;");
        yAxis1.setStyle("-fx-background-color: red;");
        yAxis2.setStyle("-fx-background-color: magenta;");
        yAxis3.setStyle("-fx-background-color: pink;");
        xAxis1.setSide(Side.BOTTOM);
        xAxis2.setSide(Side.BOTTOM);
        xAxis3.setSide(Side.TOP);
        yAxis1.setSide(Side.LEFT);
        yAxis2.setSide(Side.CENTER_VER);
        yAxis3.setSide(Side.LEFT);
        chart.axes.addAll(xAxis1, yAxis1, xAxis2, xAxis3, yAxis2, yAxis3);
        Renderer renderer = new ReducingLineRenderer();
        chart.renderers.setAll(renderer);
        DataSet testdata = new SineFunction("sine function", 100);
        renderer.getDatasets().add(testdata);

        return new StackPane(chart);
    }

    public static void main(final String[] args) {
        Application.launch(args);
    }
}
