/*
 * Decompiled with CFR 0.152.
 */
package com.badmelon.farming.entity;

import java.time.LocalDateTime;

public class Setting {
    private Long id;
    private String settingKey;
    private String settingValue;
    private String description;
    private Integer isSecret;
    private LocalDateTime updatedAt;

    public Long getId() {
        return this.id;
    }

    public String getSettingKey() {
        return this.settingKey;
    }

    public String getSettingValue() {
        return this.settingValue;
    }

    public String getDescription() {
        return this.description;
    }

    public Integer getIsSecret() {
        return this.isSecret;
    }

    public LocalDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setIsSecret(Integer isSecret) {
        this.isSecret = isSecret;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Setting)) {
            return false;
        }
        Setting other = (Setting)o;
        if (!other.canEqual(this)) {
            return false;
        }
        Long this$id = this.getId();
        Long other$id = other.getId();
        if (this$id == null ? other$id != null : !((Object)this$id).equals(other$id)) {
            return false;
        }
        Integer this$isSecret = this.getIsSecret();
        Integer other$isSecret = other.getIsSecret();
        if (this$isSecret == null ? other$isSecret != null : !((Object)this$isSecret).equals(other$isSecret)) {
            return false;
        }
        String this$settingKey = this.getSettingKey();
        String other$settingKey = other.getSettingKey();
        if (this$settingKey == null ? other$settingKey != null : !this$settingKey.equals(other$settingKey)) {
            return false;
        }
        String this$settingValue = this.getSettingValue();
        String other$settingValue = other.getSettingValue();
        if (this$settingValue == null ? other$settingValue != null : !this$settingValue.equals(other$settingValue)) {
            return false;
        }
        String this$description = this.getDescription();
        String other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description)) {
            return false;
        }
        LocalDateTime this$updatedAt = this.getUpdatedAt();
        LocalDateTime other$updatedAt = other.getUpdatedAt();
        return !(this$updatedAt == null ? other$updatedAt != null : !((Object)this$updatedAt).equals(other$updatedAt));
    }

    protected boolean canEqual(Object other) {
        return other instanceof Setting;
    }

    public int hashCode() {
        int PRIME = 59;
        int result = 1;
        Long $id = this.getId();
        result = result * 59 + ($id == null ? 43 : ((Object)$id).hashCode());
        Integer $isSecret = this.getIsSecret();
        result = result * 59 + ($isSecret == null ? 43 : ((Object)$isSecret).hashCode());
        String $settingKey = this.getSettingKey();
        result = result * 59 + ($settingKey == null ? 43 : $settingKey.hashCode());
        String $settingValue = this.getSettingValue();
        result = result * 59 + ($settingValue == null ? 43 : $settingValue.hashCode());
        String $description = this.getDescription();
        result = result * 59 + ($description == null ? 43 : $description.hashCode());
        LocalDateTime $updatedAt = this.getUpdatedAt();
        result = result * 59 + ($updatedAt == null ? 43 : ((Object)$updatedAt).hashCode());
        return result;
    }

    public String toString() {
        return "Setting(id=" + this.getId() + ", settingKey=" + this.getSettingKey() + ", settingValue=" + this.getSettingValue() + ", description=" + this.getDescription() + ", isSecret=" + this.getIsSecret() + ", updatedAt=" + String.valueOf(this.getUpdatedAt()) + ")";
    }
}

