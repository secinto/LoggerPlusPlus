package com.nccgroup.loggerplusplus.exports;

import com.coreyd97.BurpExtenderUtilities.Alignment;
import com.coreyd97.BurpExtenderUtilities.ComponentGroup;
import com.coreyd97.BurpExtenderUtilities.PanelBuilder;
import com.coreyd97.BurpExtenderUtilities.Preferences;
import com.nccgroup.loggerplusplus.LoggerPlusPlus;
import com.nccgroup.loggerplusplus.filter.logfilter.LogTableFilter;
import com.nccgroup.loggerplusplus.filter.parser.ParseException;
import com.nccgroup.loggerplusplus.logentry.LogEntryField;
import com.nccgroup.loggerplusplus.util.Globals;
import com.nccgroup.loggerplusplus.util.MoreHelp;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;

import static com.nccgroup.loggerplusplus.util.Globals.*;

public class GraylogExporterConfigDialog extends JDialog {

    GraylogExporterConfigDialog(Frame owner, GraylogExporter graylogExporter){
        super(owner, "Graylog Exporter Configuration", true);

        this.setLayout(new BorderLayout());
        Preferences preferences = graylogExporter.getPreferences();

        JTextField addressField = PanelBuilder.createPreferenceTextField(preferences, PREF_GRAYLOG_ADDRESS);
        JSpinner graylogPortSpinner = PanelBuilder.createPreferenceSpinner(preferences, PREF_GRAYLOG_PORT);
        ((SpinnerNumberModel) graylogPortSpinner.getModel()).setMaximum(65535);
        ((SpinnerNumberModel) graylogPortSpinner.getModel()).setMinimum(0);
        graylogPortSpinner.setEditor(new JSpinner.NumberEditor(graylogPortSpinner, "#"));

        JComboBox<Protocol> protocolSelector = new JComboBox<>(Protocol.values());
        protocolSelector.setSelectedItem(preferences.getSetting(PREF_GRAYLOG_PROTOCOL));
        protocolSelector.addActionListener(actionEvent -> {
            graylogExporter.getPreferences().setSetting(PREF_GRAYLOG_PROTOCOL, protocolSelector.getSelectedItem());
        });

        JTextField apiTokenField = PanelBuilder.createPreferencePasswordField(preferences, PREF_GRAYLOG_API_TOKEN);

        JCheckBox compressionCheckbox = PanelBuilder.createPreferenceCheckBox(preferences, PREF_GRAYLOG_COMPRESSION_ENABLED);

        JSpinner graylogDelaySpinner = PanelBuilder.createPreferenceSpinner(preferences, PREF_GRAYLOG_DELAY);
        ((SpinnerNumberModel) graylogDelaySpinner.getModel()).setMaximum(99999);
        ((SpinnerNumberModel) graylogDelaySpinner.getModel()).setMinimum(10);
        ((SpinnerNumberModel) graylogDelaySpinner.getModel()).setStepSize(10);

        JButton configureFieldsButton = new JButton(new AbstractAction("Configure") {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                List<LogEntryField> selectedFields = MoreHelp.showFieldChooserDialog(addressField,
                        preferences, "Graylog Exporter", graylogExporter.getFields());

                if(selectedFields == null){
                    //Cancelled.
                } else if (!selectedFields.isEmpty()) {
                    graylogExporter.setFields(selectedFields);
                } else {
                    JOptionPane.showMessageDialog(addressField,
                            "No fields were selected. No changes have been made.",
                            "Graylog Exporter", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        String projectPreviousFilterString = preferences.getSetting(Globals.PREF_GRAYLOG_FILTER_PROJECT_PREVIOUS);
        String filterString = preferences.getSetting(Globals.PREF_GRAYLOG_FILTER);
        if (projectPreviousFilterString != null && !Objects.equals(projectPreviousFilterString, filterString)) {
            int res = JOptionPane.showConfirmDialog(LoggerPlusPlus.instance.getLoggerFrame(),
                    "Looks like the log filter has been changed since you last used this Burp project.\n" +
                            "Do you want to restore the previous filter used by the project?\n" +
                            "\n" +
                            "Previously used filter: " + projectPreviousFilterString + "\n" +
                            "Current filter: " + filterString, "Graylog Exporter Log Filter",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.YES_OPTION) {
                preferences.setSetting(PREF_GRAYLOG_FILTER, projectPreviousFilterString);
            }
        }

        JTextField filterField = PanelBuilder.createPreferenceTextField(preferences, PREF_GRAYLOG_FILTER);
        filterField.setMinimumSize(new Dimension(600, 0));

        JCheckBox autostartGlobal = PanelBuilder.createPreferenceCheckBox(preferences, PREF_GRAYLOG_AUTOSTART_GLOBAL);
        JCheckBox autostartProject = PanelBuilder.createPreferenceCheckBox(preferences, PREF_GRAYLOG_AUTOSTART_PROJECT);

        //If global autostart is on, it overrides the per-project setting.
        autostartProject.setEnabled(!(boolean) preferences.getSetting(PREF_GRAYLOG_AUTOSTART_GLOBAL));
        preferences.addSettingListener((source, settingName, newValue) -> {
            if (Objects.equals(settingName, PREF_GRAYLOG_AUTOSTART_GLOBAL)) {
                autostartProject.setEnabled(!(boolean) newValue);
                if ((boolean) newValue) {
                    preferences.setSetting(PREF_GRAYLOG_AUTOSTART_PROJECT, true);
                }
            }
        });

        ComponentGroup connectionGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Connection");
        connectionGroup.addComponentWithLabel("Address: ", addressField);
        connectionGroup.addComponentWithLabel("Port: ", graylogPortSpinner);
        connectionGroup.addComponentWithLabel("Protocol: ", protocolSelector);

        ComponentGroup authGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Authentication");
        authGroup.addComponentWithLabel("API Token (Optional): ", apiTokenField);

        ComponentGroup miscGroup = new ComponentGroup(ComponentGroup.Orientation.VERTICAL, "Misc");
        miscGroup.add(PanelBuilder.build(new Component[][]{
                new JComponent[]{new JLabel("Upload Frequency (Seconds): "), graylogDelaySpinner},
                new JComponent[]{new JLabel("Enable Compression: "), compressionCheckbox},
                new JComponent[]{new JLabel("Exported Fields: "), configureFieldsButton},
                new JComponent[]{new JLabel("Log Filter: "), filterField},
                new JComponent[]{new JLabel("Autostart Exporter (All Projects): "), autostartGlobal},
                new JComponent[]{new JLabel("Autostart Exporter (This Project): "), autostartProject},
        }, new int[][]{
                new int[]{0, 1},
                new int[]{0, 1},
                new int[]{0, 1},
                new int[]{0, 1},
                new int[]{0, 1},
                new int[]{0, 1}
        }, Alignment.FILL, 1, 1));


        PanelBuilder panelBuilder = new PanelBuilder();
        panelBuilder.setComponentGrid(new JComponent[][]{
                new JComponent[]{connectionGroup},
                new JComponent[]{authGroup},
                new JComponent[]{miscGroup}
        });
        int[][] weights = new int[][]{
                new int[]{1},
                new int[]{1},
                new int[]{1},
        };
        panelBuilder.setGridWeightsY(weights)
                    .setGridWeightsX(weights)
                    .setAlignment(Alignment.CENTER)
                    .setInsetsX(5)
                    .setInsetsY(5);

        this.add(panelBuilder.build(), BorderLayout.CENTER);

        this.setMinimumSize(new Dimension(600, 200));

        this.pack();
        this.setResizable(true);
        this.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                String logFilter = preferences.getSetting(PREF_GRAYLOG_FILTER);

                if (!StringUtils.isBlank(logFilter)) {
                    try {
                        new LogTableFilter(logFilter);
                    } catch (ParseException ex) {
                        JOptionPane.showMessageDialog(GraylogExporterConfigDialog.this,
                                "Cannot save Graylog Exporter configuration. The chosen log filter is invalid: \n" +
                                        ex.getMessage(), "Invalid Graylog Exporter Configuration", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }
                GraylogExporterConfigDialog.this.dispose();
                super.windowClosing(e);
            }
        });
    }
}
