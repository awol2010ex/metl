package org.jumpmind.symmetric.is.ui.views;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.io.IOUtils;
import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.is.core.util.AbstractXMLFactory;
import org.jumpmind.symmetric.is.ui.definition.XMLComponentUI;
import org.jumpmind.symmetric.is.ui.definition.XMLUI;
import org.jumpmind.symmetric.is.ui.views.design.IComponentEditPanel;

public class UIXMLFactory extends AbstractXMLFactory implements IUIFactory {

    protected Map<String, XMLComponentUI> componentUisByComponentId;

    synchronized public IComponentEditPanel create(String componentId) {
        try {
            XMLComponentUI ui = componentUisByComponentId.get(componentId);
            if (ui != null) {
                return (IComponentEditPanel) Class.forName(ui.getClassName()).newInstance();
            } else {
                return null;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    protected void reset() {
        componentUisByComponentId = new HashMap<String, XMLComponentUI>();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void loadComponentsForClassloader(ClassLoader classLoader) {
        try {
            JAXBContext jc = JAXBContext.newInstance(XMLUI.class.getPackage().getName());
            Unmarshaller unmarshaller = jc.createUnmarshaller();

            List<InputStream> componentXmls = loadResources("ui.xml", classLoader);
            try {
                for (InputStream inputStream : componentXmls) {
                    InputStreamReader reader = new InputStreamReader(inputStream);
                    JAXBElement<XMLUI> root = (JAXBElement<XMLUI>) unmarshaller.unmarshal(reader);
                    XMLUI ui = root.getValue();
                    List<XMLComponentUI> componentUis = ui.getComponentUis();
                    for (XMLComponentUI xmlComponentUI : componentUis) {
                        componentUisByComponentId.put(xmlComponentUI.getComponentId(), xmlComponentUI);
                    }
                }
            } finally {
                for (InputStream inputStream : componentXmls) {
                    IOUtils.closeQuietly(inputStream);
                }

            }
        } catch (Exception e) {
            throw new IoException(e);
        }
    }

}
