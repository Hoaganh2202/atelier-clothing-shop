package com.shop.backend.einvoice.provider.misa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.einvoice.provider.misa")
public class MisaProperties {

    private boolean enabled = false;
    private String baseUrl = "https://testapi.meinvoice.vn";
    private String appId;
    private String taxCode;
    private String username;
    private String password;
    private String templateCode = "1";
    private String invoiceSeries = "1C26TYY";
    private long tokenExpirationBufferSeconds = 3600L;
    private long tokenFallbackTtlSeconds = 1800L; // 30 minutes safe conservative fallback
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 25;
    private boolean useCodeApi = false;
    private String lookupPortalUrl = "https://meinvoice.vn/tra-cuu";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getTaxCode() {
        return taxCode;
    }

    public void setTaxCode(String taxCode) {
        this.taxCode = taxCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getInvoiceSeries() {
        return invoiceSeries;
    }

    public void setInvoiceSeries(String invoiceSeries) {
        this.invoiceSeries = invoiceSeries;
    }

    public long getTokenExpirationBufferSeconds() {
        return tokenExpirationBufferSeconds;
    }

    public void setTokenExpirationBufferSeconds(long tokenExpirationBufferSeconds) {
        this.tokenExpirationBufferSeconds = tokenExpirationBufferSeconds;
    }

    public long getTokenFallbackTtlSeconds() {
        return tokenFallbackTtlSeconds;
    }

    public void setTokenFallbackTtlSeconds(long tokenFallbackTtlSeconds) {
        this.tokenFallbackTtlSeconds = tokenFallbackTtlSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public boolean isUseCodeApi() {
        return useCodeApi;
    }

    public void setUseCodeApi(boolean useCodeApi) {
        this.useCodeApi = useCodeApi;
    }

    public String getLookupPortalUrl() {
        return lookupPortalUrl;
    }

    public void setLookupPortalUrl(String lookupPortalUrl) {
        this.lookupPortalUrl = lookupPortalUrl;
    }

    @Override
    public String toString() {
        return "MisaProperties{" +
                "enabled=" + enabled +
                ", baseUrl='" + baseUrl + '\'' +
                ", appId='" + appId + '\'' +
                ", taxCode='" + taxCode + '\'' +
                ", username='" + username + '\'' +
                ", password='[PROTECTED]'" +
                ", templateCode='" + templateCode + '\'' +
                ", invoiceSeries='" + invoiceSeries + '\'' +
                ", tokenExpirationBufferSeconds=" + tokenExpirationBufferSeconds +
                ", tokenFallbackTtlSeconds=" + tokenFallbackTtlSeconds +
                ", connectTimeoutSeconds=" + connectTimeoutSeconds +
                ", readTimeoutSeconds=" + readTimeoutSeconds +
                ", useCodeApi=" + useCodeApi +
                ", lookupPortalUrl='" + lookupPortalUrl + '\'' +
                '}';
    }
}
