package org.jumpmind.symmetric.is.ui.diagram;

import com.vaadin.ui.Component;
import com.vaadin.ui.Component.Event;

public class ConnectionEvent extends Event {

    private static final long serialVersionUID = 1L;

    String sourceNodeId;

    String targetNodeId;

    boolean removed;

    public ConnectionEvent(Component source, String sourceNodeId, String targetNodeId,
            boolean removed) {
        super(source);
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.removed = removed;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public boolean isRemoved() {
        return removed;
    }
}
