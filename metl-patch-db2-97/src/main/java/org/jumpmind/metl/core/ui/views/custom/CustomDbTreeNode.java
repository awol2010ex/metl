package org.jumpmind.metl.core.ui.views.custom;


import com.vaadin.server.Resource;
import org.jumpmind.db.model.Table;
import org.jumpmind.db.platform.IDatabasePlatform;
import org.jumpmind.properties.TypedProperties;
import org.jumpmind.vaadin.ui.sqlexplorer.DbTree;
import org.jumpmind.vaadin.ui.sqlexplorer.IDb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CustomDbTreeNode implements Serializable {

    private static final long serialVersionUID = 1L;

    protected String name;

    protected String description;

    protected String type;

    protected Resource icon;

    protected TypedProperties properties = new TypedProperties();

    protected CustomDbTreeNode parent;

    protected List<CustomDbTreeNode> children = new ArrayList<CustomDbTreeNode>();

    protected CustomDbTree dbTree;

    public CustomDbTreeNode(CustomDbTree dbTree, String name, String type, Resource icon,
                      CustomDbTreeNode parent) {
        this.name = name;
        this.type = type;
        this.parent = parent;
        this.icon = icon;
        this.dbTree = dbTree;
    }

    public CustomDbTreeNode() {
    }

    public void setParent(CustomDbTreeNode parent) {
        this.parent = parent;
    }

    public CustomDbTreeNode getParent() {
        return parent;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return name;
    }

    public boolean hasChildren() {
        return children.size() > 0;
    }

    public List<CustomDbTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<CustomDbTreeNode> children) {
        this.children = children;
    }

    public CustomDbTreeNode find(CustomDbTreeNode node) {
        if (this.equals(node)) {
            return this;
        } else if (children != null && children.size() > 0) {
            Iterator<CustomDbTreeNode> it = children.iterator();
            while (it.hasNext()) {
                CustomDbTreeNode child = (CustomDbTreeNode) it.next();
                if (child.equals(node)) {
                    return child;
                }
            }

            for (CustomDbTreeNode child : children) {
                CustomDbTreeNode target = child.find(node);
                if (target != null) {
                    return target;
                }
            }
        }

        return null;
    }

    protected Table getTableFor() {
        IDb db = dbTree.getDbForNode(this);
        IDatabasePlatform platform = db.getPlatform();
        TypedProperties nodeProperties = getProperties();
        return platform.getTableFromCache(
                nodeProperties.get(DbTree.PROPERTY_CATALOG_NAME),
                nodeProperties.get(DbTree.PROPERTY_SCHEMA_NAME), name, false);
    }

    public boolean delete(CustomDbTreeNode node) {
        if (children != null && children.size() > 0) {
            Iterator<CustomDbTreeNode> it = children.iterator();
            while (it.hasNext()) {
                CustomDbTreeNode child = (CustomDbTreeNode) it.next();
                if (child.equals(node)) {
                    it.remove();
                    return true;
                }
            }

            for (CustomDbTreeNode child : children) {
                if (child.delete(node)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public void setIcon(Resource icon) {
        this.icon = icon;
    }

    public Resource getIcon() {
        return icon;
    }

    public List<String> findTreeNodeNamesOfType(String type) {
        List<String> names = new ArrayList<String>();
        if (this.getType().equals(type)) {
            names.add(getName());
        }
        findTreeNodeNamesOfType(type, getChildren(), names);
        return names;
    }

    public void findTreeNodeNamesOfType(String type,
                                        List<CustomDbTreeNode> treeNodes, List<String> names) {
        for (CustomDbTreeNode treeNode : treeNodes) {
            if (treeNode.getType().equals(type)) {
                names.add(treeNode.getName());
            }

            findTreeNodeNamesOfType(type, treeNode.getChildren(), names);
        }
    }

    public void setProperties(TypedProperties properties) {
        this.properties = properties;
    }

    public TypedProperties getProperties() {
        return properties;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        result = prime * result + ((parent == null) ? 0 : parent.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CustomDbTreeNode other = (CustomDbTreeNode) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (type == null) {
            if (other.type != null)
                return false;
        } else if (!type.equals(other.type))
            return false;
        if (parent == null) {
            if (other.parent != null) {
                return false;
            }
        } else if (!parent.equals(other.parent)) {
            return false;
        }
        return true;
    }

}