package com.shop.backend.einvoice.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.backend.einvoice.provider.dto.ProviderIssueRequest;
import com.shop.backend.einvoice.provider.dto.ProviderIssueResult;
import com.shop.backend.einvoice.provider.misa.MisaInvoiceProvider;
import com.shop.backend.einvoice.provider.mock.MockInvoiceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoutingInvoiceProviderTest {

    private MockInvoiceProvider mockInvoiceProvider;
    private MisaInvoiceProvider misaInvoiceProvider;
    private InvoiceProviderProperties properties;
    private RoutingInvoiceProvider routingProvider;

    @BeforeEach
    void setUp() {
        mockInvoiceProvider = mock(MockInvoiceProvider.class);
        misaInvoiceProvider = mock(MisaInvoiceProvider.class);
        properties = new InvoiceProviderProperties();
        routingProvider = new RoutingInvoiceProvider(mockInvoiceProvider, misaInvoiceProvider, properties);
    }

    @Test
    @DisplayName("Should route to MockInvoiceProvider when type is 'mock'")
    void shouldRouteToMockProviderWhenTypeIsMock() {
        properties.setType("mock");

        when(mockInvoiceProvider.getProviderName()).thenReturn("MOCK");

        assertThat(routingProvider.getDelegate()).isSameAs(mockInvoiceProvider);
        assertThat(routingProvider.getProviderName()).isEqualTo("MOCK");
    }

    @Test
    @DisplayName("Should route to MisaInvoiceProvider when type is 'misa'")
    void shouldRouteToMisaProviderWhenTypeIsMisa() {
        properties.setType("misa");

        when(misaInvoiceProvider.getProviderName()).thenReturn("MISA");

        assertThat(routingProvider.getDelegate()).isSameAs(misaInvoiceProvider);
        assertThat(routingProvider.getProviderName()).isEqualTo("MISA");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when provider type is invalid")
    void shouldThrowIllegalStateExceptionWhenTypeIsInvalid() {
        properties.setType("UNKNOWN_PROVIDER_XYZ");

        assertThatThrownBy(() -> routingProvider.getDelegate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid einvoice provider type 'unknown_provider_xyz'");
    }

    @Test
    @DisplayName("Should default to MockInvoiceProvider when type is null or empty")
    void shouldDefaultToMockWhenTypeIsNullOrEmpty() {
        properties.setType(null);
        assertThat(routingProvider.getDelegate()).isSameAs(mockInvoiceProvider);

        properties.setType("");
        assertThat(routingProvider.getDelegate()).isSameAs(mockInvoiceProvider);
    }

    @Test
    @DisplayName("Should delegate issueInvoice call to the active provider")
    void shouldDelegateIssueInvoiceToActiveProvider() {
        properties.setType("mock");
        ProviderIssueRequest request = ProviderIssueRequest.builder().build();
        ProviderIssueResult mockResult = ProviderIssueResult.builder().success(true).build();

        when(mockInvoiceProvider.issueInvoice(request)).thenReturn(mockResult);

        ProviderIssueResult result = routingProvider.issueInvoice(request);

        assertThat(result).isSameAs(mockResult);
        verify(mockInvoiceProvider).issueInvoice(request);
    }
}
