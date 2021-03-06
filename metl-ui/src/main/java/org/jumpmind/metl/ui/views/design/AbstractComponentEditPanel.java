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

import org.jumpmind.metl.core.model.Component;
import org.jumpmind.metl.core.plugin.XMLComponentDefinition;
import org.jumpmind.metl.ui.common.ApplicationContext;

import com.vaadin.ui.VerticalLayout;

public abstract class AbstractComponentEditPanel extends VerticalLayout implements IComponentEditPanel {

    private static final long serialVersionUID = 1L;
    
    public Component component;

    public ApplicationContext context;

    public PropertySheet propertySheet;

    public XMLComponentDefinition componentDefinition;

    public Component getComponent() {
        return component;
    }

    public void setComponent(Component component) {
        this.component = component;
    }

    public ApplicationContext getContext() {
        return context;
    }

    public void setContext(ApplicationContext context) {
        this.context = context;
    }

    public PropertySheet getPropertySheet() {
        return propertySheet;
    }

    public void setPropertySheet(PropertySheet propertySheet) {
        this.propertySheet = propertySheet;
    }

    public XMLComponentDefinition getComponentDefinition() {
        return componentDefinition;
    }

    public void setComponentDefinition(XMLComponentDefinition componentDefinition) {
        this.componentDefinition = componentDefinition;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean readOnly;

    @Override
    public void init(boolean readOnly, Component component, ApplicationContext context, PropertySheet propertySheet) {
        this.component = component;
        this.context  = context;
        this.componentDefinition = context.getDefinitionFactory().getComponentDefinition(component.getProjectVersionId(), component.getType());
        this.propertySheet = propertySheet;
        this.readOnly = readOnly;
        buildUI();
    }
    
    abstract protected void buildUI();
    
    @Override
    public boolean closing() {
        return true;
    }

    @Override
    public void selected() {
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override

    public void deselected() {
    }
    
    
}
