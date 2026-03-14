package org.keycloak.client.admin.cli.v2;

import java.util.List;

/**
 * Compact descriptor for v2 CLI commands.
 * Produced at build time from OpenAPI spec, cached per-server at runtime.
 * Deserialized with Jackson — no SmallRye needed on the read path.
 */
public class KcAdmV2CommandDescriptor {

    private String version;
    private List<ResourceDescriptor> resources;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public List<ResourceDescriptor> getResources() {
        return resources;
    }

    public void setResources(List<ResourceDescriptor> resources) {
        this.resources = resources;
    }

    public static class ResourceDescriptor {
        private String name;
        private List<CommandDescriptor> commands;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<CommandDescriptor> getCommands() {
            return commands;
        }

        public void setCommands(List<CommandDescriptor> commands) {
            this.commands = commands;
        }
    }

    public static class CommandDescriptor {
        private String name;
        private String httpMethod;
        private String path;
        private String description;
        private boolean requiresId;
        private List<OptionDescriptor> options;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequiresId() {
            return requiresId;
        }

        public void setRequiresId(boolean requiresId) {
            this.requiresId = requiresId;
        }

        public List<OptionDescriptor> getOptions() {
            return options;
        }

        public void setOptions(List<OptionDescriptor> options) {
            this.options = options;
        }
    }

    public static class OptionDescriptor {
        private String name;
        private String fieldName;
        private String type;
        private String description;
        private boolean array;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isArray() {
            return array;
        }

        public void setArray(boolean array) {
            this.array = array;
        }
    }
}
