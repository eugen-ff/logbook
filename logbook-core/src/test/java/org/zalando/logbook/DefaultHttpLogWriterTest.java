package org.zalando.logbook;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.zalando.logbook.DefaultLogbook.SimplePrecorrelation;

import java.io.IOException;
import java.util.List;

import static java.time.Duration.ZERO;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public final class DefaultHttpLogWriterTest {

    @Test
    void shouldDefaultToLogbookLogger() {
        final DefaultHttpLogWriter unit = new DefaultHttpLogWriter();

        assertThat(unit.getLogger(), is(equalTo(LoggerFactory.getLogger(Logbook.class))));
    }

    @Test
    void shouldDefaultToTraceLevelForActivation() throws IOException {
        final Logger logger = mock(Logger.class);
        final HttpLogWriter unit = new DefaultHttpLogWriter(logger);

        unit.isActive(mock(RawHttpRequest.class));

        verify(logger).isTraceEnabled();
    }

    @Test
    void shouldDefaultToTraceLevelForLoggingRequests() throws IOException {
        final Logger logger = mock(Logger.class);
        final HttpLogWriter unit = new DefaultHttpLogWriter(logger);

        unit.writeRequest(new SimplePrecorrelation<>("1", "foo", MockHttpRequest.create()
                .withOrigin(Origin.LOCAL)));

        final ArgumentCaptor<Marker> captor = ArgumentCaptor.forClass(Marker.class);
        verify(logger).trace(captor.capture(), eq("foo"));

        final Marker marker = captor.getValue();
        assertTrue(marker.contains("request"));
        assertTrue(marker.contains("local"));
    }

    @Test
    void shouldDefaultToTraceLevelForLoggingResponses() throws IOException {
        final Logger logger = mock(Logger.class);
        final HttpLogWriter unit = new DefaultHttpLogWriter(logger);

        unit.writeResponse(new DefaultLogbook.SimpleCorrelation<>("1", ZERO, "foo", "bar",
                MockHttpRequest.create(), MockHttpResponse.create()
                .withOrigin(Origin.REMOTE)
                .withStatus(404)));

        final ArgumentCaptor<Marker> captor = ArgumentCaptor.forClass(Marker.class);
        verify(logger).trace(captor.capture(), eq("bar"));

        final Marker marker = captor.getValue();
        assertTrue(marker.contains("response"));
        assertTrue(marker.contains("remote"));
        assertTrue(marker.contains("4xx"));
    }

    @Test
    void shouldCacheMarker() throws IOException {
        final Logger logger = mock(Logger.class);
        final HttpLogWriter unit = new DefaultHttpLogWriter(logger);

        final Precorrelation<String> precorrelation = new SimplePrecorrelation<>("1", "foo", MockHttpRequest.create());
        unit.writeRequest(precorrelation);
        unit.writeRequest(precorrelation);

        final ArgumentCaptor<Marker> captor = ArgumentCaptor.forClass(Marker.class);
        verify(logger, times(2)).trace(captor.capture(), eq("foo"));
        final List<Marker> markers = captor.getAllValues();

        assertThat(markers, is(not(empty())));

        for (final Marker left : markers) {
            for (final Marker right : markers) {
                assertThat(left, is(sameInstance(right)));
            }
        }
    }

}
