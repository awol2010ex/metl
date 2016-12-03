package org.jumpmind.metl.core.ui.views.custom;

import com.vaadin.event.Action;
import com.vaadin.server.Resource;

import java.util.Set;

abstract public class CustomDbTreeAction extends Action {

    private static final long serialVersionUID = 1L;

    public CustomDbTreeAction(String caption) {
        super(caption);
    }

    public CustomDbTreeAction(String caption, Resource icon) {
        super(caption, icon);
    }

    abstract public void handle(Set<CustomDbTreeNode> nodes);

}
