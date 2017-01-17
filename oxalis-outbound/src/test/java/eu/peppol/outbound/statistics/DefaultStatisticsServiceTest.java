/*
 * Copyright (c) 2010 - 2015 Norwegian Agency for Pupblic Government and eGovernment (Difi)
 *
 * This file is part of Oxalis.
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by the European Commission
 * - subsequent versions of the EUPL (the "Licence"); You may not use this work except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl5
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the Licence
 *  is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

package eu.peppol.outbound.statistics;

import brave.Tracer;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import eu.peppol.identifier.AccessPointIdentifier;
import eu.peppol.identifier.MessageId;
import eu.peppol.outbound.guice.TestResourceModule;
import eu.peppol.outbound.lookup.MockLookupModule;
import eu.peppol.outbound.transmission.TransmissionRequestBuilder;
import eu.peppol.outbound.transmission.TransmissionTestModule;
import eu.peppol.statistics.*;
import eu.peppol.util.GlobalConfiguration;
import no.difi.oxalis.api.outbound.TransmissionRequest;
import no.difi.oxalis.api.outbound.TransmissionResponse;
import no.difi.oxalis.api.statistics.StatisticsService;
import no.difi.oxalis.commons.mode.ModeModule;
import no.difi.oxalis.commons.tracing.TracingModule;
import no.difi.oxalis.test.security.CertificateMock;
import no.difi.vefa.peppol.common.model.Digest;
import no.difi.vefa.peppol.common.model.Endpoint;
import no.difi.vefa.peppol.common.model.Header;
import no.difi.vefa.peppol.common.model.Receipt;
import org.easymock.EasyMock;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.testng.Assert.*;

/**
 * @author steinar
 * @author thore
 * @author erlend
 */
@Guice(modules = {TransmissionTestModule.class, TestResourceModule.class, MockLookupModule.class, ModeModule.class, TracingModule.class})
public class DefaultStatisticsServiceTest {

    @Inject
    private Injector injector;

    @Inject
    private GlobalConfiguration globalConfiguration;

    @Inject
    private Tracer tracer;

    @BeforeMethod
    public void setUp() {
        globalConfiguration.setTransmissionBuilderOverride(true);
    }

    private TransmissionResponse transmissionResponse = new TransmissionResponse() {
        @Override
        public MessageId getMessageId() {
            return new MessageId();
        }

        @Override
        public Header getHeader() {
            return null;
        }

        @Override
        public List<Receipt> getReceipts() {
            return Collections.emptyList();
        }

        @Override
        public Endpoint getEndpoint() {
            return null;
        }

        @Override
        public Receipt primaryReceipt() {
            return null;
        }

        @Override
        public Digest getDigest() {
            return null;
        }
    };


    /**
     * Verifies persistence of the raw statistics.
     *
     * @throws Exception
     */
    @Test
    public void testPersistStatistics() throws Exception {
        MockLookupModule.resetService();

        final TransmissionRequest transmissionRequest = injector.getInstance(TransmissionRequestBuilder.class)
                .payLoad(injector.getInstance(Key.get(InputStream.class, Names.named("sample-xml-with-sbdh"))))
                .build();

        RawStatisticsRepository mockRawStatisticsRepository = EasyMock.createMock(RawStatisticsRepository.class);
        StatisticsService statisticsService = new DefaultStatisticsService(mockRawStatisticsRepository, CertificateMock.withCN("AP_TEST"), tracer);

        // Expect the raw statistics repository to be invoked
        EasyMock.expect(mockRawStatisticsRepository.persist(EasyMock.isA(RawStatistics.class))).andDelegateTo(new RawStatisticsRepository() {
            @Override
            public Integer persist(RawStatistics rawStatistics) {
                assertNotNull(rawStatistics.getReceiver());
                assertNotNull(rawStatistics.getSender());
                assertNotNull(rawStatistics.getAccessPointIdentifier());
                assertEquals(rawStatistics.getAccessPointIdentifier(), new AccessPointIdentifier("AP_TEST"));
                assertNotNull(rawStatistics.getDate());
                assertNotEquals(rawStatistics.getDate(), transmissionRequest.getPeppolStandardBusinessHeader().getCreationDateAndTime());

                assertEquals(rawStatistics.getDirection(), Direction.OUT);
                assertEquals(rawStatistics.getPeppolDocumentTypeId(), transmissionRequest.getPeppolStandardBusinessHeader().getDocumentTypeIdentifier());

                assertNotNull(rawStatistics.getChannelId());
                assertNotNull(rawStatistics.getPeppolProcessTypeId());
                return 42;  // Fake primary key from the database.
            }

            @Override
            public void fetchAndTransformRawStatistics(StatisticsTransformer transformer, Date start, Date end, StatisticsGranularity granularity) {
            }
        });

        EasyMock.replay(mockRawStatisticsRepository);
        statisticsService.persist(transmissionRequest, transmissionResponse, tracer.newTrace().name("unit test").start());
    }

    @Test // This is not supposed to throw an exception.
    public void triggerException() throws Exception {
        MockLookupModule.resetService();

        final TransmissionRequest transmissionRequest = injector.getInstance(TransmissionRequestBuilder.class)
                .payLoad(injector.getInstance(Key.get(InputStream.class, Names.named("sample-xml-with-sbdh"))))
                .build();

        RawStatisticsRepository rawStatisticsRepository = Mockito.mock(RawStatisticsRepository.class);
        Mockito.when(rawStatisticsRepository.persist(Mockito.any(RawStatistics.class))).thenThrow(new RuntimeException("From unit test"));

        StatisticsService statisticsService = new DefaultStatisticsService(rawStatisticsRepository, CertificateMock.withCN("AP_TEST"), tracer);
        statisticsService.persist(transmissionRequest, transmissionResponse, tracer.newTrace().name("unit test").start());
    }
}
