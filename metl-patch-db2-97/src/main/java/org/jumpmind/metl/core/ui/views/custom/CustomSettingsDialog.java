package org.jumpmind.metl.core.ui.views.custom;

import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.ui.*;
import com.vaadin.ui.Button.ClickEvent;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ResizableWindow;
import org.jumpmind.vaadin.ui.sqlexplorer.ISettingsProvider;
import org.jumpmind.vaadin.ui.sqlexplorer.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;

import static org.jumpmind.vaadin.ui.sqlexplorer.Settings.*;

public class CustomSettingsDialog extends ResizableWindow {

    private static final long serialVersionUID = 1L;

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private TextField rowsToFetchField;

    private CheckBox autoCommitBox;

    private TextField delimiterField;

    private TextField excludeTablesWithPrefixField;

    private CheckBox resultAsTextBox;

    private CheckBox ignoreErrorsWhenRunningScript;

    private CheckBox showRowNumbersBox;

    private CheckBox showResultsInNewTabsBox;

    ISettingsProvider settingsProvider;

    CustomSqlExplorer explorer;

    public CustomSettingsDialog(CustomSqlExplorer explorer) {
        super("Settings");
        this.explorer = explorer;
        this.settingsProvider = explorer.getSettingsProvider();
        setWidth(400, Unit.PIXELS);
        addComponent(createSettingsLayout(), 1);
        addComponent(createButtonLayout());
    }

    protected AbstractLayout createSettingsLayout() {
        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setMargin(new MarginInfo(false, true, false, true));
        layout.addStyleName("v-scrollable");
        FormLayout settingsLayout = new FormLayout();

        Settings settings = settingsProvider.get();
        TypedProperties properties = settings.getProperties();

        rowsToFetchField = new TextField("Max Results");
        rowsToFetchField.setColumns(6);
        rowsToFetchField.setValidationVisible(true);
        rowsToFetchField.setConverter(Integer.class);
        rowsToFetchField.setValue(properties.getProperty(Settings.SQL_EXPLORER_MAX_RESULTS, "100"));
        settingsLayout.addComponent(rowsToFetchField);

        delimiterField = new TextField("Delimiter");
        delimiterField.setValue(properties.getProperty(Settings.SQL_EXPLORER_DELIMITER, ";"));
        settingsLayout.addComponent(delimiterField);

        excludeTablesWithPrefixField = new TextField("Hide Tables (regex)");
        excludeTablesWithPrefixField.setValue(properties
                .getProperty(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX));
        settingsLayout.addComponent(excludeTablesWithPrefixField);

        resultAsTextBox = new CheckBox("Result As Text");
        String resultAsTextValue = (properties.getProperty(Settings.SQL_EXPLORER_RESULT_AS_TEXT, "false"));
        if (resultAsTextValue.equals("true")) {
            resultAsTextBox.setValue(true);
        } else {
            resultAsTextBox.setValue(false);
        }
        settingsLayout.addComponent(resultAsTextBox);

        ignoreErrorsWhenRunningScript = new CheckBox("Ignore Errors When Running Scripts");
        String ignoreErrorsWhenRunningScriptTextValue = (properties.getProperty(Settings.SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS, "false"));
        if (ignoreErrorsWhenRunningScriptTextValue.equals("true")) {
            ignoreErrorsWhenRunningScript.setValue(true);
        } else {
            ignoreErrorsWhenRunningScript.setValue(false);
        }
        settingsLayout.addComponent(ignoreErrorsWhenRunningScript);

        autoCommitBox = new CheckBox("Auto Commit");
        String autoCommitValue = (properties.getProperty(Settings.SQL_EXPLORER_AUTO_COMMIT, "true"));
        if (autoCommitValue.equals("true")) {
            autoCommitBox.setValue(true);
        } else {
            autoCommitBox.setValue(false);
        }
        settingsLayout.addComponent(autoCommitBox);

        showRowNumbersBox = new CheckBox("Show Row Numbers");
        String showRowNumbersValue = (properties.getProperty(Settings.SQL_EXPLORER_SHOW_ROW_NUMBERS, "true"));
        if (showRowNumbersValue.equals("true")) {
            showRowNumbersBox.setValue(true);
        } else {
            showRowNumbersBox.setValue(false);
        }
        settingsLayout.addComponent(showRowNumbersBox);

        showResultsInNewTabsBox = new CheckBox("Always Put Results In New Tabs");
        String showResultsInNewTabsValue = (properties.getProperty(Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS, "false"));
        if (showResultsInNewTabsValue.equals("true")) {
            showResultsInNewTabsBox.setValue(true);
        } else {
            showResultsInNewTabsBox.setValue(false);
        }
        settingsLayout.addComponent(showResultsInNewTabsBox);

        layout.addComponent(settingsLayout);
        return layout;

    }

    protected AbstractLayout createButtonLayout() {
        Button saveButton = CommonUiUtils.createPrimaryButton("Save", new Button.ClickListener() {
            private static final long serialVersionUID = 1L;

            public void buttonClick(ClickEvent event) {
                save();
                UI.getCurrent().removeWindow(CustomSettingsDialog.this);
            }
        });

        return buildButtonFooter(new Button("Cancel", new CloseButtonListener()), saveButton);
    }

    protected void save() {
        Settings settings = settingsProvider.get();
        TypedProperties properties = settings.getProperties();

        try {
            rowsToFetchField.validate();
            properties.setProperty(Settings.SQL_EXPLORER_MAX_RESULTS, new DecimalFormat().parse(rowsToFetchField.getValue()).intValue());
            properties.setProperty(Settings.SQL_EXPLORER_AUTO_COMMIT,
                    String.valueOf(autoCommitBox.getValue()));
            properties.setProperty(Settings.SQL_EXPLORER_DELIMITER, delimiterField.getValue());
            properties.setProperty(Settings.SQL_EXPLORER_RESULT_AS_TEXT,
                    String.valueOf(resultAsTextBox.getValue()));
            properties.setProperty(Settings.SQL_EXPLORER_SHOW_ROW_NUMBERS,
                    String.valueOf(showRowNumbersBox.getValue()));
            properties.setProperty(Settings.SQL_EXPLORER_EXCLUDE_TABLES_REGEX,
                    excludeTablesWithPrefixField.getValue());
            properties.setProperty(Settings.SQL_EXPLORER_IGNORE_ERRORS_WHEN_RUNNING_SCRIPTS, String.valueOf(ignoreErrorsWhenRunningScript.getValue()));
            properties.setProperty(Settings.SQL_EXPLORER_SHOW_RESULTS_IN_NEW_TABS, String.valueOf(showResultsInNewTabsBox.getValue()));
            settingsProvider.save(settings);
            explorer.refreshQueryPanels();
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            CommonUiUtils.notify(ex);
        }
    }
}

