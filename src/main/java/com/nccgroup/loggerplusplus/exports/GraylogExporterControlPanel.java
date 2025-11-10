package com.nccgroup.loggerplusplus.exports;

import com.coreyd97.BurpExtenderUtilities.Alignment;
import com.coreyd97.BurpExtenderUtilities.PanelBuilder;
import com.nccgroup.loggerplusplus.LoggerPlusPlus;
import com.nccgroup.loggerplusplus.util.Globals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.ExecutionException;

public class GraylogExporterControlPanel extends JPanel {

    private final GraylogExporter graylogExporter;
    private static final String STARTING_TEXT = "Starting Graylog Exporter...";
    private static final String STOPPING_TEXT = "Stopping Graylog Exporter...";
    private static final String START_TEXT = "Start Graylog Exporter";
    private static final String STOP_TEXT = "Stop Graylog Exporter";

    Logger logger = LogManager.getLogger(this);

    public GraylogExporterControlPanel(GraylogExporter graylogExporter) {
        this.graylogExporter = graylogExporter;
        this.setLayout(new BorderLayout());

        JButton showConfigDialogButton = new JButton(new AbstractAction("Configure Graylog Exporter") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                new GraylogExporterConfigDialog(LoggerPlusPlus.instance.getLoggerFrame(), graylogExporter)
                        .setVisible(true);

                //Dialog closed. Update previous project entry filter to current value.
                String newFilter = graylogExporter.getPreferences().getSetting(Globals.PREF_GRAYLOG_FILTER);
                graylogExporter.getPreferences().setSetting(Globals.PREF_GRAYLOG_FILTER_PROJECT_PREVIOUS, newFilter);
            }
        });

        JToggleButton exportButton = new JToggleButton("Start Graylog Exporter");
        exportButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                boolean buttonNowActive = exportButton.isSelected();
                exportButton.setEnabled(false);
                exportButton.setText(buttonNowActive ? STARTING_TEXT : STOPPING_TEXT);
                new SwingWorker<Boolean, Void>(){
                    Exception exception;

                    @Override
                    protected Boolean doInBackground() throws Exception {
                        boolean success = false;
                        try {
                            if (exportButton.isSelected()) {
                                enableExporter();
                            } else {
                                disableExporter();
                            }
                            success = true;
                        }catch (Exception e){
                            this.exception = e;
                        }
                        return success;
                    }

                    @Override
                    protected void done() {
                        try {
                            if(exception != null) {
                                JOptionPane.showMessageDialog(exportButton, "Could not start Graylog exporter: " +
                                        exception.getMessage() + "\nSee the logs for more information.", "Graylog Exporter", JOptionPane.ERROR_MESSAGE);
                                logger.error("Could not start Graylog exporter.", exception);
                            }
                            Boolean success = get();
                            boolean isRunning = buttonNowActive ^ !success;
                            exportButton.setSelected(isRunning);
                            showConfigDialogButton.setEnabled(!isRunning);

                            exportButton.setText(isRunning ? STOP_TEXT : START_TEXT);

                        } catch (InterruptedException | ExecutionException e) {
                            e.printStackTrace();
                        }
                        exportButton.setEnabled(true);
                    }
                }.execute();
            }
        });

        if (isExporterEnabled()){
            exportButton.setSelected(true);
            exportButton.setText(STOP_TEXT);
            showConfigDialogButton.setEnabled(false);
        }


        this.add(PanelBuilder.build(new JComponent[][]{
                new JComponent[]{showConfigDialogButton},
                new JComponent[]{exportButton}
        }, new int[][]{
                new int[]{1},
                new int[]{1}
        }, Alignment.FILL, 1.0, 1.0), BorderLayout.CENTER);


        this.setBorder(BorderFactory.createTitledBorder("Graylog Exporter"));
    }

    private void enableExporter() throws Exception {
        this.graylogExporter.getExportController().enableExporter(this.graylogExporter);
    }

    private void disableExporter() throws Exception {
        this.graylogExporter.getExportController().disableExporter(this.graylogExporter);
    }

    private boolean isExporterEnabled() {
        return this.graylogExporter.getExportController().getEnabledExporters().contains(this.graylogExporter);
    }

}
