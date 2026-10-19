/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.entity;

import java.time.LocalDateTime;

public class Teammate {
    private Long id;
    private String name;
    private String role;
    private String status;
    private String currentTask;
    private Integer progress;
    private String logSummary;
    private LocalDateTime heartbeatAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public String getRole() {
        return this.role;
    }

    public String getStatus() {
        return this.status;
    }

    public String getCurrentTask() {
        return this.currentTask;
    }

    public Integer getProgress() {
        return this.progress;
    }

    public String getLogSummary() {
        return this.logSummary;
    }

    public LocalDateTime getHeartbeatAt() {
        return this.heartbeatAt;
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

    public void setRole(String role) {
        this.role = role;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setCurrentTask(String currentTask) {
        this.currentTask = currentTask;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public void setLogSummary(String logSummary) {
        this.logSummary = logSummary;
    }

    public void setHeartbeatAt(LocalDateTime heartbeatAt) {
        this.heartbeatAt = heartbeatAt;
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
        if (!(o instanceof Teammate)) {
            return false;
        }
        Teammate other = (Teammate)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Integer this$progress = this.getProgress();
        Integer other$progress = other.getProgress();
        if (this$progress == null ? other$progress != null : !((Object)this$progress).equals(other$progress)) {
            return false;
        }
        String this$name = this.getName();
        String other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) {
            return false;
        }
        String this$role = this.getRole();
        String other$role = other.getRole();
        if (this$role == null ? other$role != null : !this$role.equals(other$role)) {
            return false;
        }
        String this$status = this.getStatus();
        String other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        String this$currentTask = this.getCurrentTask();
        String other$currentTask = other.getCurrentTask();
        if (this$currentTask == null ? other$currentTask != null : !this$currentTask.equals(other$currentTask)) {
            return false;
        }
        String this$logSummary = this.getLogSummary();
        String other$logSummary = other.getLogSummary();
        if (this$logSummary == null ? other$logSummary != null : !this$logSummary.equals(other$logSummary)) {
            return false;
        }
        LocalDateTime this$heartbeatAt = this.getHeartbeatAt();
        LocalDateTime other$heartbeatAt = other.getHeartbeatAt();
        if (this$heartbeatAt == null ? other$heartbeatAt != null : !((Object)this$heartbeatAt).equals(other$heartbeatAt)) {
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
        return other instanceof Teammate;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Integer $progress = this.getProgress();
        result = result * 59 + ($progress == null ? 43 : ((Object)$progress).hashCode());
        String $name = this.getName();
        result = result * 59 + ($name == null ? 43 : $name.hashCode());
        String $role = this.getRole();
        result = result * 59 + ($role == null ? 43 : $role.hashCode());
        String $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        String $currentTask = this.getCurrentTask();
        result = result * 59 + ($currentTask == null ? 43 : $currentTask.hashCode());
        String $logSummary = this.getLogSummary();
        result = result * 59 + ($logSummary == null ? 43 : $logSummary.hashCode());
        LocalDateTime $heartbeatAt = this.getHeartbeatAt();
        result = result * 59 + ($heartbeatAt == null ? 43 : ((Object)$heartbeatAt).hashCode());
        LocalDateTime $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
        LocalDateTime $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : ((Object)$updatedAt).hashCode());
        return result;
    }

    public String toString() {
        return "Teammate(id=" + this.getId() + ", name=" + this.getName() + ", role=" + this.getRole() + ", status=" + this.getStatus() + ", currentTask=" + this.getCurrentTask() + ", progress=" + this.getProgress() + ", logSummary=" + this.getLogSummary() + ", heartbeatAt=" + String.valueOf(this.getHeartbeatAt()) + ", createdAt=" + String.valueOf(this.getCreatedAt()) + ", updatedAt=" + String.valueOf(this.getUpdatedAt()) + ")";
    }
}

