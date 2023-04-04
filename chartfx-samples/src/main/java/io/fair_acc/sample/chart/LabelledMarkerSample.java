package io.fair_acc.sample.chart;

import io.fair_acc.chartfx.Chart;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.stage.Stage;

import io.fair_acc.chartfx.XYChartCss;
import io.fair_acc.chartfx.axes.spi.DefaultNumericAxis;
import io.fair_acc.chartfx.renderer.spi.LabelledMarkerRenderer;
import io.fair_acc.dataset.spi.DoubleDataSet;

/**
 * Example to illustrate the use and customisation of the LabelledMarkerRenderer
 *
 * @author rstein
 */
public class LabelledMarkerSample extends ChartSample {
    private static final int N_SAMPLES = 10;

    @Override
    public Node getChartPanel(final Stage primaryStage) {
        final Chart chart = new Chart();
        chart.getAxes().addAll(new DefaultNumericAxis(), new DefaultNumericAxis());
        chart.getRenderers().set(0, new LabelledMarkerRenderer());
        chart.legendVisibleProperty().set(true);

        final DoubleDataSet dataSet = new DoubleDataSet("myData");

        for (int n = 0; n < LabelledMarkerSample.N_SAMPLES; n++) {
            if (n != 4) {
                dataSet.add(n, n, "DataLabel#" + n);
            } else {
                // index '4' has no label and is not drawn
                dataSet.add(n, n);
            }
            // for DataSets where the add(..) does not allow for a label
            // dataSet.add(n, n);
            // dataSet.addDataLabel(n, "DataLabel#" + n);

            // n=0..2 -> default style

            // style definitions/string available in XYChartCss.STROKE_WIDTH, ...
            if (n == 3) {
                dataSet.addDataStyle(n, "strokeColor=red");
                // alt:
                // dataSet.addDataStyle(n, "strokeColor:red");
            }

            // n == 4 has no label

            if (n == 5) {
                dataSet.addDataStyle(n, "strokeColor=blue; fillColor= blue; strokeDashPattern=3,5,8,5");
            }

            if (n == 6) {
                dataSet.addDataStyle(n, "strokeColor=0xEE00EE; strokeDashPattern=5,8,5,16; fillColor=0xEE00EE");
            }

            if (n == 7) {
                dataSet.addDataStyle(n, "strokeWidth=3;" + XYChartCss.FONT + "=\"Serif\";" + XYChartCss.FONT_SIZE
                                                + "=20;" + XYChartCss.FONT_POSTURE + "=italic;" + XYChartCss.FONT_WEIGHT + "=black;");
            }

            if (n == 8) {
                dataSet.addDataStyle(n,
                        "strokeWidth=3;" + XYChartCss.FONT + "=\"monospace\";" + XYChartCss.FONT_POSTURE + "=italic;");
            }
        }
        chart.getDatasets().add(dataSet);

        return chart;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}