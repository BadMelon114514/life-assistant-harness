/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HarnessTask {
    private Long id;
    private String taskKey;
    private String title;
    private String description;
    private String status;
    private String blockedBy;
    private String owner;
    private Integer progress;
    private String result;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public List<String> getBlockedKeys() {
        ArrayList<String> list = new ArrayList<String>();
        if (this.blockedBy != null && !this.blockedBy.isBlank()) {
            for (String s : this.blockedBy.split(",")) {
                if (s.isBlank()) continue;
                list.add(s.trim());
            }
        }
        return list;
    }

    public Long getId() {
        return this.id;
    }

    public String getTaskKey() {
        return this.taskKey;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getStatus() {
        return this.status;
    }

    public String getBlockedBy() {
        return this.blockedBy;
    }

    public String getOwner() {
        return this.owner;
    }

    public Integer getProgress() {
        return this.progress;
    }

    public String getResult() {
        return this.result;
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

    public void setTaskKey(String taskKey) {
        this.taskKey = taskKey;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setBlockedBy(String blockedBy) {
        this.blockedBy = blockedBy;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public void setResult(String result) {
        this.result = result;
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
        if (!(o instanceof HarnessTask)) {
            return false;
        }
        HarnessTask other = (HarnessTask)o;
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
        String this$taskKey = this.getTaskKey();
        String other$taskKey = other.getTaskKey();
        if (this$taskKey == null ? other$taskKey != null : !this$taskKey.equals(other$taskKey)) {
            return false;
        }
        String this$title = this.getTitle();
        String other$title = other.getTitle();
        if (this$title == null ? other$title != null : !this$title.equals(other$title)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        String this$status = this.getStatus();
        String other$status = other.getStatus();
        if (this$status == null ? other$status != null : !this$status.equals(other$status)) {
            return false;
        }
        String this$blockedBy = this.getBlockedBy();
        String other$blockedBy = other.getBlockedBy();
        if (this$blockedBy == null ? other$blockedBy != null : !this$blockedBy.equals(other$blockedBy)) {
            return false;
        }
        String this$owner = this.getOwner();
        String other$owner = other.getOwner();
        if (this$owner == null ? other$owner != null : !this$owner.equals(other$owner)) {
            return false;
        }
        String this$result = this.getResult();
        String other$result = other.getResult();
        if (this$result == null ? other$result != null : !this$result.equals(other$result)) {
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
        return other instanceof HarnessTask;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Integer $progress = this.getProgress();
        result = result * 59 + ($progress == null ? 43 : ((Object)$progress).hashCode());
        String $taskKey = this.getTaskKey();
        result = result * 59 + ($taskKey == null ? 43 : $taskKey.hashCode());
        String $title = this.getTitle();
        result = result * 59 + ($title == null ? 43 : $title.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        String $status = this.getStatus();
        result = result * 59 + ($status == null ? 43 : $status.hashCode());
        String $blockedBy = this.getBlockedBy();
        result = result * 59 + ($blockedBy == null ? 43 : $blockedBy.hashCode());
        String $owner = this.getOwner();
        result = result * 59 + ($owner == null ? 43 : $owner.hashCode());
        String $result = this.getResult();
        result = result * 59 + ($result == null ? 43 : $result.hashCode());
        LocalDateTime $createdAt = this.getCreatedAt();
        result = result * 59 + ($createdAt == null ? 43 : ((Object)$createdAt).hashCode());
        LocalDateTime $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : ((Object)$updatedAt).hashCode());
        return result;
    }

    public String toString() {
        return "HarnessTask(id=" + this.getId() + ", taskKey=" + this.getTaskKey() + ", title=" + this.getTitle() + ", description=" + this.getDescription() + ", status=" + this.getStatus() + ", blockedBy=" + this.getBlockedBy() + ", owner=" + this.getOwner() + ", progress=" + this.getProgress() + ", result=" + this.getResult() + ", createdAt=" + String.valueOf(this.getCreatedAt()) + ", updatedAt=" + String.valueOf(this.getUpdatedAt()) + ")";
    }
}

