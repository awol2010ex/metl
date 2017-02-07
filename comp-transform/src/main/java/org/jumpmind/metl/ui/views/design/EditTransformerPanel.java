/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.metl.ui.views.design;

import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jumpmind.metl.core.model.ComponentAttributeSetting;
import org.jumpmind.metl.core.model.Model;
import org.jumpmind.metl.core.model.ModelAttribute;
import org.jumpmind.metl.core.model.ModelEntity;
import org.jumpmind.metl.core.runtime.component.ModelAttributeScriptHelper;
import org.jumpmind.metl.core.runtime.component.Transformer;
import org.jumpmind.metl.ui.common.ButtonBar;
import org.jumpmind.metl.ui.common.UiUtils;
import org.jumpmind.vaadin.ui.common.CommonUiUtils;
import org.jumpmind.vaadin.ui.common.ExportDialog;
import org.jumpmind.vaadin.ui.common.ResizableWindow;
import org.vaadin.aceeditor.AceEditor;
import org.vaadin.aceeditor.AceMode;

import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.event.ItemClickEvent;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.AbstractSelect;
import com.vaadin.ui.AbstractTextField.TextChangeEventMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnGenerator;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;

@SuppressWarnings("serial")
public class EditTransformerPanel extends AbstractComponentEditPanel {

    Table table = new Table();

    Table exportTable = new Table();

    TextField filterField;

    AbstractSelect filterPopField;

    List<ComponentAttributeSetting> componentAttributes;

    BeanItemContainer<ComponentAttributeSetting> container = new BeanItemContainer<ComponentAttributeSetting>(
            ComponentAttributeSetting.class);
    BeanItemContainer<Record> exportContainer = new BeanItemContainer<Record>(Record.class);

    static final String SHOW_ALL = "Show All";
    static final String SHOW_POPULATED_ENTITIES = "Show Entities with Transforms";
    static final String SHOW_POPULATED_ATTRIBUTES = "Show Attributes with Transforms";

    protected void buildUI() {
        ButtonBar buttonBar = new ButtonBar();
        addComponent(buttonBar);

        filterPopField = new ComboBox();
        filterPopField.addItem(SHOW_ALL);
        filterPopField.addItem(SHOW_POPULATED_ENTITIES);
        filterPopField.addItem(SHOW_POPULATED_ATTRIBUTES);
        if (component.getInputModel() != null) {
            for (ModelEntity entity : component.getInputModel().getModelEntities()) {
                filterPopField.addItem(entity.getName());
            }
        }
        filterPopField.setNullSelectionAllowed(false);
        filterPopField.setImmediate(true);
        filterPopField.setWidth(20, Unit.EM);
        filterPopField.setValue(SHOW_ALL);
        filterPopField.addValueChangeListener(event ->  {
            if (isNotBlank(filterField.getValue())) {
                filterField.clear();
            }
            updateTable();
        });
        buttonBar.addLeft(filterPopField);

        buttonBar.addButtonRight("Export", FontAwesome.DOWNLOAD, (e) -> export());

        filterField = buttonBar.addFilter();
        filterField.addTextChangeListener(event -> {
            String text = event.getText();
            filterPopField.setValue(SHOW_ALL);
            updateTable(text);
        });

        addComponent(buttonBar);

        table.setContainerDataSource(container);

        table.setSelectable(true);
        table.setSortEnabled(false);
        table.setImmediate(true);
        table.setSortEnabled(true);
        table.setSizeFull();
        table.addGeneratedColumn("entityName", new ColumnGenerator() {

            @Override
            public Object generateCell(Table source, Object itemId, Object columnId) {
                ComponentAttributeSetting setting = (ComponentAttributeSetting) itemId;
                Model model = component.getInputModel();
                ModelAttribute attribute = model.getAttributeById(setting.getAttributeId());
                ModelEntity entity = model.getEntityById(attribute.getEntityId());
                return UiUtils.getName(filterField.getValue(), entity.getName());
            }
        });
        table.addGeneratedColumn("attributeName", new ColumnGenerator() {

            @Override
            public Object generateCell(Table source, Object itemId, Object columnId) {
                ComponentAttributeSetting setting = (ComponentAttributeSetting) itemId;
                Model model = component.getInputModel();
                ModelAttribute attribute = model.getAttributeById(setting.getAttributeId());
                return UiUtils.getName(filterField.getValue(), attribute.getName());
            }
        });
        table.addGeneratedColumn("attributeValue", new ColumnGenerator() {

            @Override
            public Object generateCell(Table source, Object itemId, Object columnId) {
                ComponentAttributeSetting setting = (ComponentAttributeSetting) itemId;
                String value = setting.getValue();
                if (value != null) {
                    // Show first line only. Display ellipses in the case of an ommision.
                    String[] lines = value.split("\r\n|\r|\n", 2);
                    if (lines.length > 1) {
                        value = lines[0] + "...";
                    }
                }
                return value;
            }
        });
        table.addGeneratedColumn("editButton", new ColumnGenerator() {

            @Override
            public Object generateCell(Table source, Object itemId, Object columnId) {
                ComponentAttributeSetting setting = (ComponentAttributeSetting) itemId;
                Button button = new Button();
                button.setIcon(FontAwesome.GEAR);
                button.addClickListener((event) -> new EditTransformWindow(setting).showAtSize(.75));
                return button;
            }
        });
        
        // Edit by double clicking or clicking the edit button.
        table.addItemClickListener(new ItemClickEvent.ItemClickListener() {
            @Override
            public void itemClick(ItemClickEvent event) {
                if (event.isDoubleClick()) {
                    new EditTransformWindow((ComponentAttributeSetting)event.getItemId()).showAtSize(.75);
                }
            }
        });
        
        table.setVisibleColumns(new Object[] { "entityName", "attributeName", "attributeValue", "editButton" });
        table.setColumnWidth("entityName", 250);
        table.setColumnWidth("attributeName", 250);
        table.setColumnHeaders(new String[] { "Entity Name", "Attribute Name", "Transform", "Edit" });
        table.setColumnExpandRatio("attributeValue", 1);
        table.setEditable(true);
        addComponent(table);
        setExpandRatio(table, 1.0f);
        

        if (component.getInputModel() != null) {

            componentAttributes = component.getAttributeSettings();

            List<ComponentAttributeSetting> toRemove = new ArrayList<ComponentAttributeSetting>();
            for (ComponentAttributeSetting componentAttribute : componentAttributes) {
                Model model = component.getInputModel();
                ModelAttribute attribute1 = model.getAttributeById(componentAttribute.getAttributeId());
                if (attribute1 == null) {
                    /*
                     * invalid attribute. model must have changed. lets remove
                     * it
                     */
                    toRemove.add(componentAttribute);
                }
            }

            for (ComponentAttributeSetting componentAttributeSetting : toRemove) {
                componentAttributes.remove(componentAttributeSetting);
                context.getConfigurationService().delete(componentAttributeSetting);
            }

            for (ModelEntity entity : component.getInputModel().getModelEntities()) {
                for (ModelAttribute attr : entity.getModelAttributes()) {
                    boolean found = false;
                    for (ComponentAttributeSetting componentAttribute : componentAttributes) {
                        if (componentAttribute.getAttributeId().equals(attr.getId())
                                && componentAttribute.getName().equals(Transformer.TRANSFORM_EXPRESSION)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        componentAttributes
                                .add(new ComponentAttributeSetting(attr.getId(), component.getId(), Transformer.TRANSFORM_EXPRESSION, null));
                    }
                }
            }

            Collections.sort(componentAttributes, new Comparator<ComponentAttributeSetting>() {
                @Override
                public int compare(ComponentAttributeSetting o1, ComponentAttributeSetting o2) {
                    Model model = component.getInputModel();
                    ModelAttribute attribute1 = model.getAttributeById(o1.getAttributeId());
                    ModelEntity entity1 = model.getEntityById(attribute1.getEntityId());

                    ModelAttribute attribute2 = model.getAttributeById(o2.getAttributeId());
                    ModelEntity entity2 = model.getEntityById(attribute2.getEntityId());

                    int compare = entity1.getName().compareTo(entity2.getName());
                    if (compare == 0) {
                        compare = attribute1.getName().compareTo(attribute2.getName());
                    }
                    return compare;
                }
            });
        }

        updateTable(null);

        exportTable.setContainerDataSource(exportContainer);
        exportTable.setVisibleColumns(new Object[] { "entityName", "attributeName", "value" });
        exportTable.setColumnHeaders(new String[] { "Entity Name", "Attribute Name", "Transform" });
    }
    
    protected void updateTable() {
        String filter = (String)filterPopField.getValue();
        if (filter.equals(SHOW_ALL) || filter.equals(SHOW_POPULATED_ATTRIBUTES) || filter.equals(SHOW_POPULATED_ENTITIES)) {
            filter = null;
        }
        updateTable(filter);
    }

    protected void updateTable(String filter) {
        boolean showPopulatedEntities = filterPopField.getValue().equals(SHOW_POPULATED_ENTITIES);
        boolean showPopulatedAttributes = filterPopField.getValue().equals(SHOW_POPULATED_ATTRIBUTES);

        if (componentAttributes != null) {
            Model model = component.getInputModel();
            Collection<String> entityNames = new ArrayList<>();

            filter = filter != null ? filter.toLowerCase() : null;
            if (model != null) {
                table.removeAllItems();
                // loop through the attributes with transforms to get a list of
                // entities
                for (ComponentAttributeSetting componentAttribute : componentAttributes) {
                    ModelAttribute attribute = model.getAttributeById(componentAttribute.getAttributeId());
                    ModelEntity entity = model.getEntityById(attribute.getEntityId());
                    if (isNotBlank(componentAttribute.getValue()) && !entityNames.contains(entity.getName())) {
                        entityNames.add(entity.getName());
                    }
                }

                for (ComponentAttributeSetting componentAttribute : componentAttributes) {
                    ModelAttribute attribute = model.getAttributeById(componentAttribute.getAttributeId());
                    ModelEntity entity = model.getEntityById(attribute.getEntityId());

                    boolean populated = (showPopulatedEntities && entityNames.contains(entity.getName()))
                            || (showPopulatedAttributes && isNotBlank(componentAttribute.getValue()))
                            || (!showPopulatedAttributes && !showPopulatedEntities);
                    if (isBlank(filter) || entity.getName().toLowerCase().contains(filter)
                            || attribute.getName().toLowerCase().contains(filter)) {
                        if (populated) {
                            table.addItem(componentAttribute);
                        }
                    }
                }
            }
        }
    }

    protected void export() {
        exportTable.removeAllItems();
        updateExportTable(filterField.getValue());
        String fileNamePrefix = component.getName().toLowerCase().replace(' ', '-');
        ExportDialog dialog = new ExportDialog(exportTable, fileNamePrefix, component.getName());
        UI.getCurrent().addWindow(dialog);
    }

    protected void updateExportTable(String filter) {
        boolean showPopulatedEntities = filterPopField.getValue().equals(SHOW_POPULATED_ENTITIES);
        boolean showPopulatedAttributes = filterPopField.getValue().equals(SHOW_POPULATED_ATTRIBUTES);

        if (componentAttributes != null) {
            Model model = component.getInputModel();
            Collection<String> entityNames = new ArrayList<>();

            filter = filter != null ? filter.toLowerCase() : null;
            if (model != null) {
                exportTable.removeAllItems();
                // loop through the attributes with transforms to get a list of
                // entities
                for (ComponentAttributeSetting componentAttribute : componentAttributes) {
                    ModelAttribute attribute = model.getAttributeById(componentAttribute.getAttributeId());
                    ModelEntity entity = model.getEntityById(attribute.getEntityId());
                    if (isNotBlank(componentAttribute.getValue()) && !entityNames.contains(entity.getName())) {
                        entityNames.add(entity.getName());
                    }
                }

                for (ComponentAttributeSetting componentAttribute : componentAttributes) {
                    ModelAttribute attribute = model.getAttributeById(componentAttribute.getAttributeId());
                    ModelEntity entity = model.getEntityById(attribute.getEntityId());

                    boolean populated = (showPopulatedEntities && entityNames.contains(entity.getName()))
                            || (showPopulatedAttributes && isNotBlank(componentAttribute.getValue()))
                            || (!showPopulatedAttributes && !showPopulatedEntities);
                    if (isBlank(filter) || entity.getName().toLowerCase().contains(filter)
                            || attribute.getName().toLowerCase().contains(filter)) {
                        if (populated) {
                            exportTable.addItem(new Record(entity, attribute));
                        }
                    }
                }
            }
        }
    }

    public class Record {
        ModelEntity modelEntity;

        ModelAttribute modelAttribute;

        String entityName = "";

        String attributeName = "";

        String value = "";

        public Record(ModelEntity modelEntity, ModelAttribute modelAttribute) {
            this.modelEntity = modelEntity;
            this.modelAttribute = modelAttribute;

            if (modelEntity != null) {
                this.entityName = modelEntity.getName();
            }

            if (modelAttribute != null) {
                this.attributeName = modelAttribute.getName();
                ComponentAttributeSetting setting = component.getSingleAttributeSetting(modelAttribute.getId(),
                        Transformer.TRANSFORM_EXPRESSION);
                if (setting != null) {
                    this.value = setting.getValue();
                }
            }
        }

        public int hashCode() {
            return modelEntity.hashCode() + modelAttribute.hashCode();
        }

        public String getEntityName() {
            return modelEntity.getName();
        }

        public String getAttributeName() {
            return modelAttribute.getName();
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
    
    class EditTransformWindow extends ResizableWindow {
        private static final long serialVersionUID = 1L;
        
        AceEditor editor;

        public EditTransformWindow(ComponentAttributeSetting setting) {
            super("Transform");
            setWidth(800f, Unit.PIXELS);
            setHeight(600f, Unit.PIXELS);
            content.setMargin(true);
            
            ButtonBar buttonBar = new ButtonBar();
            addComponent(buttonBar);
            
            ComboBox combo = new ComboBox();
            combo.setWidth(400, Unit.PIXELS);
            String[] functions = ModelAttributeScriptHelper.getSignatures();
            for (String function : functions) {
                combo.addItem(function);
            }
            combo.setValue(combo.getItemIds().iterator().next());
            combo.setNullSelectionAllowed(false);
            combo.setPageLength(functions.length > 20 ? 20 : functions.length);
            combo.setImmediate(true);
            
            buttonBar.addLeft(combo);

            buttonBar.addButton("Insert", FontAwesome.SIGN_IN,
                    new ClickListener() {
                            
                        @Override
                        public void buttonClick(ClickEvent event) {
                            String script  = (editor.getValue()==null) ? "" : editor.getValue();
                            StringBuilder builder = new StringBuilder(script);
                            String substring = (String) combo.getValue();
                            int startPosition = editor.getCursorPosition();
                            builder.insert(startPosition, substring);
                            editor.setValue(builder.toString());
                            editor.setSelection(startPosition, startPosition + substring.length());
                            // Manually save text since TextChangeListener is not firing.
                            setting.setValue(editor.getValue());
                            EditTransformerPanel.this.context.getConfigurationService()
                                    .save(setting);
                        }
                    });
            
            
            editor = CommonUiUtils.createAceEditor();
            editor.setTextChangeEventMode(TextChangeEventMode.LAZY);
            editor.setTextChangeTimeout(200);
            editor.setMode(AceMode.java);
            
            editor.addTextChangeListener(new TextChangeListener() {

                @Override
                public void textChange(TextChangeEvent event) {
                    setting.setValue(event.getText());
                    EditTransformerPanel.this.context.getConfigurationService()
                            .save(setting);
                }
            });
            editor.setValue(setting.getValue());
            
            content.addComponent(editor);
            content.setExpandRatio(editor, 1);
            
            addComponent(buildButtonFooter(buildCloseButton()));
            
        }
        
        @Override
        public void close() {
            super.close();
            updateTable();
        }

    }
}
