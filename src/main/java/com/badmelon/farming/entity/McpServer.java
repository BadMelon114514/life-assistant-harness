/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.entity;

import java.time.LocalDateTime;

public class McpServer {
    private Long id;
    private String name;
    private String serverType;
    private String endpoint;
    private String description;
    private String config;
    private String status;
    private LocalDateTime lastChecked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getServerType() {
        return this.serverType;
    }

    public String getEndpoint() {
        return this.endpoint;
    }

    public String getDescription() {
        return this.description;
    }

    public String getConfig() {
        return this.config;
    }

    public String getStatus() {
        return this.status;
    }

    public LocalDateTime getLastChecked() {
        return this.lastChecked;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setLastChecked(LocalDateTime lastChecked) {
        this.lastChecked = lastChecked;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof McpServer)) {
            return false;
        }
        McpServer other = (McpServer)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        String this$name = this.getName();
        String other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        String this$serverType = this.getServerType();
        String other$serverType = other.getServerType();
        if (this$serverType == null ? other$serverType != null : !this$serverType.equals(other$serverType)) {
            return false;
        }
        String this$endpoint = this.getEndpoint();
        String other$endpoint = other.getEndpoint();
        if (this$endpoint == null ? other$endpoint != null : !this$endpoint.equals(other$endpoint)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        String this$config = this.getConfig();
        String other$config = other.getConfig();
        if (this$config == null ? other$config != null : !this$config.equals(other$config)) {
            return false;
        }
        String this$status = this.getStatus();
        String other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        LocalDateTime this$lastChecked = this.getLastChecked();
        LocalDateTime other$lastChecked = other.getLastChecked();
        if (this$lastChecked == null ? other$lastChecked != null : !((Object)this$lastChecked).equals(other$lastChecked)) {
            return false;
        }
        LocalDateTime this$createdAt = this.getCreatedAt();
        LocalDateTime other$createdAt = other.getCreatedAt();
        if (this$createdAt == null ? other$createdAt != null : !((Object)this$createdAt).equals(other$createdAt)) {
            return false;
        }
        LocalDateTime this$updatedAt = this.getUpdatedAt();
        LocalDateTime other$updatedAt = other.getUpdatedAt();
        return !(this$updatedAt == null ? other$updatedAt != null : !((Object)this$updatedAt).equals(other$updatedAt));
    }

    protected boolean canEqual(Object other) {
        return other instanceof McpServer;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        String $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        String $serverType = this.getServerType();
        result = result * 59 + ($serverType == null ? 43 : $serverType.hashCode());
        String $endpoint = this.getEndpoint();
        result = result * 59 + ($endpoint == null ? 43 : $endpoint.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        String $config = this.getConfig();
        result = result * 59 + ($config == null ? 43 : $config.hashCode());
        String $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        LocalDateTime $lastChecked = this.getLastChecked();
        result = result * 59 + ($lastChecked == null ? 43 : ((Object)$lastChecked).hashCode());
        LocalDateTime $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
        LocalDateTime $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : ((Object)$updatedAt).hashCode());
        return result;
    }

    public String toString() {
        return "McpServer(id=" + this.getId() + ", name=" + this.getName() + ", serverType=" + this.getServerType() + ", endpoint=" + this.getEndpoint() + ", description=" + this.getDescription() + ", config=" + this.getConfig() + ", status=" + this.getStatus() + ", lastChecked=" + String.valueOf(this.getLastChecked()) + ", createdAt=" + String.valueOf(this.getCreatedAt()) + ", updatedAt=" + String.valueOf(this.getUpdatedAt()) + ")";
    }
}

