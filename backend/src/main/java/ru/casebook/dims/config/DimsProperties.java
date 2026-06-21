package ru.casebook.dims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dims")
public class DimsProperties {
    private int fileUploadMaxMb = 20;
    private int fileUploadPlatformMaxMb = 50;
    private String storagePath = "./storage";
    private int auditRetentionDays = 180;
    private int reportMediaMemoryLimitMb = 20;

    public int getFileUploadMaxMb() {
        return fileUploadMaxMb;
    }

    public void setFileUploadMaxMb(int fileUploadMaxMb) {
        this.fileUploadMaxMb = fileUploadMaxMb;
    }

    public int getFileUploadPlatformMaxMb() {
        return fileUploadPlatformMaxMb;
    }

    public void setFileUploadPlatformMaxMb(int fileUploadPlatformMaxMb) {
        this.fileUploadPlatformMaxMb = fileUploadPlatformMaxMb;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public int getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public void setAuditRetentionDays(int auditRetentionDays) {
        this.auditRetentionDays = auditRetentionDays;
    }

    public int getReportMediaMemoryLimitMb() { return reportMediaMemoryLimitMb; }
    public void setReportMediaMemoryLimitMb(int reportMediaMemoryLimitMb) { this.reportMediaMemoryLimitMb = reportMediaMemoryLimitMb; }
}
